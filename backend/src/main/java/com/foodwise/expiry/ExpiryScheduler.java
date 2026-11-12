package com.foodwise.expiry;

import com.foodwise.common.AppClock;
import com.foodwise.common.enums.NotificationType;
import com.foodwise.config.FoodwiseProperties;
import com.foodwise.inventory.InventoryBatch;
import com.foodwise.inventory.InventoryBatchRepository;
import com.foodwise.kitchen.KitchenMember;
import com.foodwise.kitchen.KitchenMemberRepository;
import com.foodwise.leftover.LeftoverRule;
import com.foodwise.leftover.LeftoverRuleRepository;
import com.foodwise.notification.NotificationService;
import com.foodwise.user.UserPreferences;
import com.foodwise.user.UserPreferencesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Background alerts (SYSTEM_DESIGN 8). Runs every 15 minutes; each user receives at most one digest per kitchen
 * per day once their configured digest time is reached. Idempotency is enforced by the database
 * (uq_notifications_dedupe), so restarts, overlapping runs and multiple instances never duplicate alerts.
 */
@Component
public class ExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryScheduler.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final FoodwiseProperties props;
    private final AppClock clock;
    private final UserPreferencesRepository prefsRepo;
    private final KitchenMemberRepository memberRepo;
    private final InventoryBatchRepository batchRepo;
    private final LeftoverRuleRepository ruleRepo;
    private final NotificationService notifications;

    public ExpiryScheduler(FoodwiseProperties props, AppClock clock, UserPreferencesRepository prefsRepo,
                           KitchenMemberRepository memberRepo, InventoryBatchRepository batchRepo,
                           LeftoverRuleRepository ruleRepo, NotificationService notifications) {
        this.props = props;
        this.clock = clock;
        this.prefsRepo = prefsRepo;
        this.memberRepo = memberRepo;
        this.batchRepo = batchRepo;
        this.ruleRepo = ruleRepo;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${foodwise.scheduler.digest-cron:0 0/15 * * * *}", zone = "${foodwise.timezone:Asia/Kolkata}")
    public void scheduledDigest() {
        if (props.scheduler().enabled()) {
            processDailyExpiryDigests();
        }
    }

    @Scheduled(cron = "${foodwise.scheduler.expired-cron:0 30 8 * * *}", zone = "${foodwise.timezone:Asia/Kolkata}")
    public void scheduledExpired() {
        if (props.scheduler().enabled()) {
            notifyNewlyExpiredBatches();
        }
    }

    /** Creates due morning digests and leftover alerts. Returns the number of notifications created. */
    public int processDailyExpiryDigests() {
        LocalDate today = clock.today();
        LocalTime window = clock.timeOfDay().truncatedTo(ChronoUnit.MINUTES);
        Map<UUID, List<InventoryBatch>> urgentByKitchen = new HashMap<>();
        int created = 0;
        for (UserPreferences prefs : prefsRepo.findDigestDue(window)) {
            try {
                for (KitchenMember m : memberRepo.findByUserId(prefs.getUserId())) {
                    int horizon = Math.max(1, prefs.getNotifyExpiryDaysBefore());
                    List<InventoryBatch> urgent = urgentByKitchen
                            .computeIfAbsent(m.getKitchenId(), k -> batchRepo.findActiveExpiringOnOrBefore(k, today.plusDays(7)))
                            .stream().filter(b -> !b.getExpiryDate().isAfter(today.plusDays(horizon))).toList();
                    if (!urgent.isEmpty() && digest(prefs.getUserId(), m.getKitchenId(), urgent, today)) {
                        created++;
                    }
                    created += leftoverAlerts(prefs.getUserId(), m.getKitchenId(), urgent, today);
                }
            } catch (RuntimeException e) {
                log.error("Digest failed for user {}", prefs.getUserId(), e);
            }
        }
        if (created > 0) {
            log.info("Expiry digest run created {} notifications", created);
        }
        return created;
    }

    private boolean digest(UUID userId, UUID kitchenId, List<InventoryBatch> urgent, LocalDate today) {
        long expired = urgent.stream().filter(b -> b.getExpiryDate().isBefore(today)).count();
        List<InventoryBatch> upcoming = urgent.stream().filter(b -> !b.getExpiryDate().isBefore(today)).toList();
        String title = upcoming.isEmpty()
                ? expired + (expired == 1 ? " item is" : " items are") + " past expiry"
                : upcoming.size() + (upcoming.size() == 1 ? " item needs" : " items need") + " using soon";
        StringBuilder msg = new StringBuilder();
        upcoming.stream().limit(4).forEach(b -> msg.append(msg.isEmpty() ? "" : ", ").append(b.getCustomItemName())
                .append(" (").append(when(b.getExpiryDate(), today)).append(')'));
        if (upcoming.size() > 4) {
            msg.append(" and ").append(upcoming.size() - 4).append(" more");
        }
        if (expired > 0) {
            msg.append(msg.isEmpty() ? "" : ". ").append(expired).append(" already expired - check and log them.");
        } else {
            msg.append(". Find a recipe that uses them.");
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("batchIds", urgent.stream().map(b -> b.getId().toString()).collect(Collectors.toList()));
        meta.put("date", today.toString());
        return notifications.notifyOnce(userId, kitchenId, NotificationType.EXPIRY_WARNING, title, msg.toString(),
                "/expiry", "digest:" + today + ":" + kitchenId, meta);
    }

    /** US-04: on a leftover's last day, suggest a rule-based repurposing idea. */
    private int leftoverAlerts(UUID userId, UUID kitchenId, List<InventoryBatch> urgent, LocalDate today) {
        int created = 0;
        for (InventoryBatch b : urgent) {
            if (!b.isLeftover() || !b.getExpiryDate().equals(today)) {
                continue;
            }
            Optional<LeftoverRule> idea = ruleRepo.findByLeftoverTypeOrderByDisplayOrder(b.getLeftoverType()).stream().findFirst();
            String message = idea.map(r -> "Make " + r.getTitle() + " today with your remaining " + b.getCustomItemName() + ".")
                    .orElse("Your " + b.getCustomItemName() + " should be eaten today.");
            if (notifications.notifyOnce(userId, kitchenId, NotificationType.LEFTOVER_ALERT,
                    b.getCustomItemName() + " is due today", message, "/leftovers",
                    "leftover:" + b.getId() + ":" + today, Map.of("batchId", b.getId().toString()))) {
                created++;
            }
        }
        return created;
    }

    /** Daily: one consolidated alert per kitchen member for batches that expired yesterday (status stays active). */
    public int notifyNewlyExpiredBatches() {
        LocalDate today = clock.today();
        Map<UUID, List<InventoryBatch>> byKitchen = batchRepo.findAllActiveExpiringOn(today.minusDays(1)).stream()
                .collect(Collectors.groupingBy(InventoryBatch::getKitchenId, LinkedHashMap::new, Collectors.toList()));
        int created = 0;
        for (Map.Entry<UUID, List<InventoryBatch>> e : byKitchen.entrySet()) {
            List<InventoryBatch> items = e.getValue();
            String names = items.stream().limit(5).map(InventoryBatch::getCustomItemName).collect(Collectors.joining(", "));
            String title = items.size() == 1 ? items.getFirst().getCustomItemName() + " has expired" : items.size() + " items have expired";
            String message = names + (items.size() > 5 ? " and more" : "") + ". Check them and mark as used or log as waste.";
            for (KitchenMember m : memberRepo.findByKitchenIdOrderByJoinedAt(e.getKey())) {
                try {
                    if (notifications.notifyOnce(m.getUserId(), e.getKey(), NotificationType.ITEM_EXPIRED, title, message,
                            "/expiry", "expired:" + today + ":" + e.getKey(),
                            Map.of("batchIds", items.stream().map(b -> b.getId().toString()).toList()))) {
                        created++;
                    }
                } catch (RuntimeException ex) {
                    log.error("Expired alert failed for user {}", m.getUserId(), ex);
                }
            }
        }
        return created;
    }

    static String when(LocalDate expiry, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, expiry);
        if (days == 0) {
            return "today";
        }
        if (days == 1) {
            return "tomorrow";
        }
        return days > 1 ? "in " + days + " days" : DAY.format(expiry);
    }
}
