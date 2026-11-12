package com.foodwise.analytics;

import com.foodwise.common.ApiException;
import com.foodwise.common.AppClock;
import com.foodwise.security.KitchenAuthGuard;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rupee spending and waste analytics (Roadmap 7.B4). All aggregation happens in SQL; month boundaries are computed
 * in the kitchen time zone.
 * <ul>
 *   <li>spend = sum of grocery purchases dated in the month</li>
 *   <li>waste = sum of food_waste_logs.estimated_loss_inr logged in the month</li>
 *   <li>rescued = value of quantities consumed while the batch was within 0-3 days of expiry
 *       (cost x consumed / initial) - food that was "saved just in time"</li>
 * </ul>
 */
@Service
public class AnalyticsService {

    private static final String RESCUED_SQL = """
            SELECT COALESCE(SUM(b.cost_inr * (-e.quantity_delta) / b.initial_quantity), 0)
            FROM inventory_events e JOIN inventory_batches b ON b.id = e.batch_id
            WHERE e.kitchen_id = :k AND e.event_type IN ('consumed_partially', 'consumed_fully')
              AND e.created_at >= :fromTs AND e.created_at < :toTs AND b.cost_inr IS NOT NULL AND b.initial_quantity > 0
              AND (b.expiry_date - CAST(e.created_at AT TIME ZONE :tz AS date)) BETWEEN 0 AND 3
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final KitchenAuthGuard guard;
    private final AppClock clock;

    public AnalyticsService(NamedParameterJdbcTemplate jdbc, KitchenAuthGuard guard, AppClock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.clock = clock;
    }

    public record MonthlySummary(String month, BigDecimal totalSpendInr, BigDecimal totalWasteInr, BigDecimal rescuedInr,
                                 long purchases, long itemsWasted, long itemsUsedUp, BigDecimal previousMonthWasteInr,
                                 Integer wasteChangePercent, List<ReasonSlice> wasteByReason, List<TrendPoint> trend, String tip) {
    }

    public record CategorySlice(String category, String slug, String colorHex, BigDecimal amountInr, long count, int percent) {
    }

    public record ReasonSlice(String reason, BigDecimal amountInr, long count) {
    }

    public record TopWasted(String itemName, long times, BigDecimal totalLossInr) {
    }

    public record TrendPoint(String month, BigDecimal spendInr, BigDecimal wasteInr) {
    }

    public YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(clock.today());
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.validation("month", "Use the format YYYY-MM, e.g. 2026-10.");
        }
    }

    @Transactional(readOnly = true)
    public MonthlySummary monthlySummary(UUID kitchenId, UUID userId, YearMonth month) {
        guard.requireMembership(kitchenId, userId);
        MapSqlParameterSource p = params(kitchenId, month);
        BigDecimal spend = money("SELECT COALESCE(SUM(total_amount_inr),0) FROM grocery_purchases WHERE kitchen_id=:k "
                + "AND purchase_date >= :from AND purchase_date < :to", p);
        long purchases = count("SELECT COUNT(*) FROM grocery_purchases WHERE kitchen_id=:k AND purchase_date >= :from AND purchase_date < :to", p);
        BigDecimal waste = wasteBetween(p);
        long wasted = count("SELECT COUNT(*) FROM food_waste_logs WHERE kitchen_id=:k AND logged_at >= :fromTs AND logged_at < :toTs", p);
        long usedUp = count("SELECT COUNT(*) FROM inventory_events WHERE kitchen_id=:k AND event_type='consumed_fully' "
                + "AND created_at >= :fromTs AND created_at < :toTs", p);
        BigDecimal rescued = money(RESCUED_SQL, p);
        BigDecimal prevWaste = wasteBetween(params(kitchenId, month.minusMonths(1)));
        Integer change = prevWaste.signum() == 0 ? null
                : waste.subtract(prevWaste).multiply(BigDecimal.valueOf(100)).divide(prevWaste, 0, RoundingMode.HALF_UP).intValue();

        List<ReasonSlice> reasons = jdbc.query("""
                SELECT waste_reason, COALESCE(SUM(estimated_loss_inr),0) amount, COUNT(*) n FROM food_waste_logs
                WHERE kitchen_id=:k AND logged_at >= :fromTs AND logged_at < :toTs GROUP BY waste_reason ORDER BY amount DESC
                """, p, (rs, i) -> new ReasonSlice(rs.getString(1), rs.getBigDecimal(2).setScale(2, RoundingMode.HALF_UP), rs.getLong(3)));

        List<TrendPoint> trend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = month.minusMonths(i);
            MapSqlParameterSource mp = params(kitchenId, m);
            trend.add(new TrendPoint(m.toString(),
                    money("SELECT COALESCE(SUM(total_amount_inr),0) FROM grocery_purchases WHERE kitchen_id=:k "
                            + "AND purchase_date >= :from AND purchase_date < :to", mp),
                    wasteBetween(mp)));
        }
        List<CategorySlice> breakdown = wasteBreakdownInternal(p);
        String tip = tipFor(breakdown.isEmpty() ? null : breakdown.getFirst().slug());
        return new MonthlySummary(month.toString(), spend, waste, rescued.setScale(2, RoundingMode.HALF_UP), purchases, wasted, usedUp,
                prevWaste, change, reasons, trend, tip);
    }

    @Transactional(readOnly = true)
    public List<CategorySlice> wasteBreakdown(UUID kitchenId, UUID userId, YearMonth month) {
        guard.requireMembership(kitchenId, userId);
        return wasteBreakdownInternal(params(kitchenId, month));
    }

    private List<CategorySlice> wasteBreakdownInternal(MapSqlParameterSource p) {
        List<CategorySlice> raw = jdbc.query("""
                SELECT COALESCE(c.name, 'Other') AS name, COALESCE(c.slug, 'other') AS slug, COALESCE(c.color_hex, '#8C8A84') AS color,
                       COALESCE(SUM(w.estimated_loss_inr), 0) AS amount, COUNT(*) AS n
                FROM food_waste_logs w LEFT JOIN food_categories c ON c.id = w.category_id
                WHERE w.kitchen_id = :k AND w.logged_at >= :fromTs AND w.logged_at < :toTs
                GROUP BY 1, 2, 3 ORDER BY amount DESC, n DESC
                """, p, (rs, i) -> new CategorySlice(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getBigDecimal(4).setScale(2, RoundingMode.HALF_UP), rs.getLong(5), 0));
        BigDecimal total = raw.stream().map(CategorySlice::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCount = raw.stream().mapToLong(CategorySlice::count).sum();
        return raw.stream().map(s -> new CategorySlice(s.category(), s.slug(), s.colorHex(), s.amountInr(), s.count(),
                total.signum() > 0
                        ? s.amountInr().multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue()
                        : (int) Math.round(100.0 * s.count() / Math.max(1, totalCount)))).toList();
    }

    @Transactional(readOnly = true)
    public List<TopWasted> topWasted(UUID kitchenId, UUID userId, YearMonth month, int limit) {
        guard.requireMembership(kitchenId, userId);
        MapSqlParameterSource p = params(kitchenId, month).addValue("limit", Math.max(1, Math.min(limit, 20)));
        return jdbc.query("""
                SELECT MIN(item_name), COUNT(*) AS n, COALESCE(SUM(estimated_loss_inr), 0) AS loss FROM food_waste_logs
                WHERE kitchen_id = :k AND logged_at >= :fromTs AND logged_at < :toTs
                GROUP BY lower(item_name) ORDER BY n DESC, loss DESC LIMIT :limit
                """, p, (rs, i) -> new TopWasted(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3).setScale(2, RoundingMode.HALF_UP)));
    }

    /** Month-to-date waste and rescued values for the dashboard (no membership check: caller authorizes). */
    public Map<String, BigDecimal> monthToDate(UUID kitchenId) {
        MapSqlParameterSource p = params(kitchenId, YearMonth.from(clock.today()));
        return Map.of("wasteInr", wasteBetween(p), "rescuedInr", money(RESCUED_SQL, p).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal wasteBetween(MapSqlParameterSource p) {
        return money("SELECT COALESCE(SUM(estimated_loss_inr),0) FROM food_waste_logs WHERE kitchen_id=:k "
                + "AND logged_at >= :fromTs AND logged_at < :toTs", p);
    }

    private MapSqlParameterSource params(UUID kitchenId, YearMonth month) {
        var zone = clock.zone();
        return new MapSqlParameterSource()
                .addValue("k", kitchenId)
                .addValue("from", Date.valueOf(month.atDay(1)))
                .addValue("to", Date.valueOf(month.plusMonths(1).atDay(1)))
                .addValue("fromTs", Timestamp.from(month.atDay(1).atStartOfDay(zone).toInstant()))
                .addValue("toTs", Timestamp.from(month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()))
                .addValue("tz", zone.getId());
    }

    private BigDecimal money(String sql, MapSqlParameterSource p) {
        BigDecimal v = jdbc.queryForObject(sql, p, BigDecimal.class);
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private long count(String sql, MapSqlParameterSource p) {
        Long v = jdbc.queryForObject(sql, p, Long.class);
        return v == null ? 0 : v;
    }

    /** Qualitative handling tips only - no shelf-life durations are claimed (FR-EXP-001). */
    static String tipFor(String slug) {
        if (slug == null) {
            return "Plan two dinners around whatever expires first each week - it is the simplest way to waste less.";
        }
        return switch (slug) {
            case "vegetables" -> "Store coriander and other herbs dry, wrapped in a paper towel inside an airtight box, and cook leafy greens first.";
            case "fruits" -> "Keep bananas away from other fruit and turn overripe fruit into shakes or lassi.";
            case "dairy-eggs" -> "Keep milk and paneer on an inside shelf, not the door, and buy smaller packs if they keep going sour.";
            case "bakery" -> "Freeze half a loaf when you open it and toast slices straight from the freezer.";
            case "cooked-food" -> "Cool leftovers quickly, label the dabba with the date, and keep them at the front of the fridge.";
            case "meat-seafood" -> "Freeze meat in meal-sized portions on the day you buy it if you will not cook it soon.";
            case "grains-pulses" -> "Store atta, dal and rice in airtight containers and use older stock first.";
            default -> "Check the Expiry screen each morning and plan one meal around the most urgent item.";
        };
    }
}
