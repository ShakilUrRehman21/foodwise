package com.foodwise.dashboard;

import com.foodwise.analytics.AnalyticsService;
import com.foodwise.common.AppClock;
import com.foodwise.common.enums.BatchStatus;
import com.foodwise.common.enums.NotificationStatus;
import com.foodwise.expiry.ExpiryClassifier;
import com.foodwise.expiry.ExpiryService;
import com.foodwise.inventory.BatchDto;
import com.foodwise.inventory.BatchMapper;
import com.foodwise.inventory.InventoryBatch;
import com.foodwise.inventory.InventoryBatchRepository;
import com.foodwise.inventory.InventoryService;
import com.foodwise.mealplan.MealPlanService;
import com.foodwise.notification.NotificationRepository;
import com.foodwise.recipe.RecipeService;
import com.foodwise.security.KitchenAuthGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** "Kitchen Pulse" (UI Screen 4): everything the home screen needs in one round trip. */
@Service
public class DashboardService {

    private final InventoryBatchRepository batchRepo;
    private final BatchMapper mapper;
    private final InventoryService inventory;
    private final RecipeService recipes;
    private final MealPlanService mealPlans;
    private final NotificationRepository notificationRepo;
    private final AnalyticsService analytics;
    private final KitchenAuthGuard guard;
    private final AppClock clock;

    public DashboardService(InventoryBatchRepository batchRepo, BatchMapper mapper, InventoryService inventory, RecipeService recipes,
                            MealPlanService mealPlans, NotificationRepository notificationRepo, AnalyticsService analytics,
                            KitchenAuthGuard guard, AppClock clock) {
        this.batchRepo = batchRepo;
        this.mapper = mapper;
        this.inventory = inventory;
        this.recipes = recipes;
        this.mealPlans = mealPlans;
        this.notificationRepo = notificationRepo;
        this.analytics = analytics;
        this.guard = guard;
        this.clock = clock;
    }

    public record Counts(int total, int fridge, int freezer, int pantry, int leftovers) {
    }

    public record Pulse(Counts counts, List<BatchDto> urgent, int expiringToday, int useSoon, int expired,
                        BigDecimal valueAtRiskInr, RecipeService.RecipeSummary suggestion,
                        List<MealPlanService.MealPlanDto> todaysMeals, int lowStockCount, long unreadNotifications,
                        BigDecimal monthWasteInr, BigDecimal monthRescuedInr) {
    }

    @Transactional(readOnly = true)
    public Pulse pulse(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        LocalDate today = clock.today();
        List<InventoryBatch> active = batchRepo.findByKitchenIdAndStatus(kitchenId, BatchStatus.ACTIVE);
        int fridge = 0, freezer = 0, pantry = 0, leftovers = 0, todayCount = 0, soon = 0, expired = 0;
        BigDecimal atRisk = BigDecimal.ZERO;
        for (InventoryBatch b : active) {
            switch (b.getStorageLocation()) {
                case FRIDGE -> fridge++;
                case FREEZER -> freezer++;
                case PANTRY -> pantry++;
            }
            if (b.isLeftover()) {
                leftovers++;
            }
            switch (ExpiryClassifier.classify(b.getExpiryDate(), today)) {
                case EXPIRING_TODAY -> {
                    todayCount++;
                    atRisk = atRisk.add(ExpiryService.remainingValue(b));
                }
                case USE_SOON -> soon++;
                case EXPIRED -> expired++;
                default -> { }
            }
        }
        List<BatchDto> dtos = active.stream().map(b -> mapper.toDto(b, today))
                .sorted(Comparator.comparing(BatchDto::expiryDate)).toList();
        List<BatchDto> urgent = dtos.stream().filter(d -> d.expiryStatus().isUrgent()).limit(10).toList();
        List<RecipeService.RecipeSummary> recs = active.isEmpty() ? List.of() : recipes.recommendations(kitchenId, userId,
                new RecipeService.RecommendationFilter(null, null, false, false, null, null, false, 1));
        RecipeService.RecipeSummary suggestion = recs.isEmpty() || recs.getFirst().matchPercent() == 0 ? null : recs.getFirst();
        List<MealPlanService.MealPlanDto> meals = mealPlans.week(kitchenId, userId, null, today, 1);
        Map<String, BigDecimal> mtd = analytics.monthToDate(kitchenId);
        return new Pulse(new Counts(active.size(), fridge, freezer, pantry, leftovers), urgent, todayCount, soon, expired,
                atRisk.setScale(2, java.math.RoundingMode.HALF_UP), suggestion, meals, inventory.lowStockInternal(kitchenId).size(),
                notificationRepo.countByUserIdAndKitchenIdAndStatus(userId, kitchenId, NotificationStatus.UNREAD),
                mtd.get("wasteInr"), mtd.get("rescuedInr"));
    }
}
