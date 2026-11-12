package com.foodwise.recipe;

import com.fasterxml.jackson.annotation.JsonValue;
import com.foodwise.common.Units;
import com.foodwise.common.enums.Unit;
import com.foodwise.expiry.ExpiryStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic, weighted ingredient-availability scoring (Roadmap 5.B3). No ML.
 *
 * <pre>
 * base_weight       = 1.0 required, 0.5 optional, 0.0 pantry staple (excluded)
 * expiry_multiplier = 3.0 EXPIRING_TODAY, 2.0 USE_SOON, 1.0 otherwise
 * rank_score        = sum(matched base_weight x expiry_multiplier) / sum(base_weight)
 * </pre>
 * {@code rank_score} can exceed 1.0 - that is intended: recipes that rescue expiring food rank above equally
 * cookable ones (Palak Paneer above Aloo Gobi when paneer expires today). Readiness uses plain coverage:
 * READY_TO_COOK = every required tracked ingredient in stock in sufficient quantity; ALMOST_COOKABLE = 1-2
 * missing with at least half covered; otherwise NOT_READY.
 */
@Component
public class RecipeMatchingService {

    public enum Readiness {
        READY_TO_COOK("ready_to_cook"), ALMOST_COOKABLE("almost_cookable"), NOT_READY("not_ready");

        private final String value;

        Readiness(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum LineStatus {
        AVAILABLE("available"), INSUFFICIENT("insufficient"), MISSING("missing"), STAPLE("staple"), UNTRACKED("untracked");

        private final String value;

        LineStatus(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    /** Stock of one catalog item held in one active batch. */
    public record Portion(BigDecimal quantity, Unit unit, ExpiryStatus status) {
    }

    public record IngredientMatch(UUID catalogItemId, String name, BigDecimal requiredQuantity, Unit unit, boolean optional,
                                  boolean pantryStaple, LineStatus status, BigDecimal availableQuantity,
                                  ExpiryStatus expiryStatus, boolean unitMismatch) {
    }

    public record Match(Readiness readiness, int matchPercent, double rankScore, int requiredCount, int missingCount,
                        List<IngredientMatch> ingredients, List<String> usesExpiring, List<String> missing) {
    }

    public Match match(List<RecipeIngredient> ingredients, double servingsFactor, Map<UUID, List<Portion>> stock) {
        List<IngredientMatch> lines = new ArrayList<>();
        double totalWeight = 0;
        double matchedWeight = 0;
        int required = 0;
        int requiredAvailable = 0;
        List<String> expiring = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (RecipeIngredient ing : ingredients) {
            BigDecimal needed = ing.getQuantity().multiply(BigDecimal.valueOf(servingsFactor)).setScale(2, RoundingMode.HALF_UP);
            if (ing.isPantryStaple()) {
                lines.add(new IngredientMatch(ing.getCatalogItemId(), ing.getIngredientName(), needed, ing.getUnit(),
                        ing.isOptional(), true, LineStatus.STAPLE, null, null, false));
                continue;
            }
            if (ing.getCatalogItemId() == null) {
                lines.add(new IngredientMatch(null, ing.getIngredientName(), needed, ing.getUnit(), ing.isOptional(), false,
                        LineStatus.UNTRACKED, null, null, false));
                continue;
            }
            double base = ing.isOptional() ? 0.5 : 1.0;
            totalWeight += base;
            if (!ing.isOptional()) {
                required++;
            }

            List<Portion> portions = stock.getOrDefault(ing.getCatalogItemId(), List.of());
            BigDecimal available = BigDecimal.ZERO;
            boolean anyConvertible = false;
            ExpiryStatus mostUrgent = null;
            for (Portion p : portions) {
                if (p.quantity().signum() <= 0) {
                    continue;
                }
                mostUrgent = moreUrgent(mostUrgent, p.status());
                var converted = Units.convert(p.quantity(), p.unit(), ing.getUnit());
                if (converted.isPresent()) {
                    anyConvertible = true;
                    available = available.add(converted.get());
                }
            }
            boolean present = mostUrgent != null;
            boolean unitMismatch = present && !anyConvertible;
            LineStatus status;
            if (!present) {
                status = LineStatus.MISSING;
            } else if (unitMismatch || available.compareTo(needed) >= 0) {
                status = LineStatus.AVAILABLE; // different unit families: presence counts as available
            } else {
                status = LineStatus.INSUFFICIENT;
            }
            double multiplier = mostUrgent == null || mostUrgent == ExpiryStatus.EXPIRED ? 1.0 : mostUrgent.rescueMultiplier();
            if (status == LineStatus.AVAILABLE) {
                matchedWeight += base * multiplier;
                if (!ing.isOptional()) {
                    requiredAvailable++;
                }
                if (mostUrgent != null && mostUrgent.isUrgent()) {
                    expiring.add(ing.getIngredientName());
                }
            } else if (status == LineStatus.INSUFFICIENT) {
                matchedWeight += base * multiplier * 0.5;
                if (!ing.isOptional()) {
                    missing.add(ing.getIngredientName());
                }
                if (mostUrgent.isUrgent()) {
                    expiring.add(ing.getIngredientName());
                }
            } else if (!ing.isOptional()) {
                missing.add(ing.getIngredientName());
            }
            lines.add(new IngredientMatch(ing.getCatalogItemId(), ing.getIngredientName(), needed, ing.getUnit(), ing.isOptional(),
                    false, status, present && anyConvertible ? available.setScale(2, RoundingMode.HALF_UP) : null,
                    mostUrgent, unitMismatch));
        }

        int missingCount = required - requiredAvailable;
        int percent = required == 0 ? 100 : (int) Math.round(100.0 * requiredAvailable / required);
        Readiness readiness = missingCount == 0 ? Readiness.READY_TO_COOK
                : missingCount <= 2 && percent >= 50 ? Readiness.ALMOST_COOKABLE : Readiness.NOT_READY;
        double rank = totalWeight == 0 ? 0 : matchedWeight / totalWeight;
        return new Match(readiness, percent, Math.round(rank * 1000) / 1000.0, required, missingCount, lines, expiring, missing);
    }

    /** Order: readiness, then rescue-weighted rank score, then quicker recipes. */
    public static Comparator<Match> ordering() {
        return Comparator.comparing(Match::readiness).thenComparing(Comparator.comparingDouble(Match::rankScore).reversed());
    }

    private static ExpiryStatus moreUrgent(ExpiryStatus a, ExpiryStatus b) {
        if (a == null) {
            return b;
        }
        return urgency(b) > urgency(a) ? b : a;
    }

    private static int urgency(ExpiryStatus s) {
        return switch (s) {
            case EXPIRING_TODAY -> 3;
            case USE_SOON -> 2;
            case FRESH -> 1;
            case EXPIRED -> 0; // expired stock should not be what a recipe is built around
        };
    }
}
