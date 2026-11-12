package com.foodwise.recipe;

import com.foodwise.catalog.CatalogService;
import com.foodwise.common.ApiException;
import com.foodwise.common.AppClock;
import com.foodwise.common.Units;
import com.foodwise.common.enums.BatchStatus;
import com.foodwise.common.enums.DietaryPreference;
import com.foodwise.common.enums.Difficulty;
import com.foodwise.common.enums.MealSlot;
import com.foodwise.common.enums.RecipeDiet;
import com.foodwise.common.enums.Unit;
import com.foodwise.expiry.ExpiryClassifier;
import com.foodwise.inventory.InventoryBatch;
import com.foodwise.inventory.InventoryBatchRepository;
import com.foodwise.inventory.InventoryOperations;
import com.foodwise.mealplan.MealPlanService;
import com.foodwise.security.KitchenAuthGuard;
import com.foodwise.user.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Recipe discovery, detail with pantry overlay, and transactional cooking (Roadmap M5). */
@Service
public class RecipeService {

    /** Catalog items a Jain diet excludes (onion, garlic and root vegetables). */
    private static final Set<String> JAIN_EXCLUDED = Set.of("onion", "garlic", "potato", "carrot", "beetroot", "radish",
            "ginger", "sweet potato", "colocasia", "yam", "shallots", "spring onion", "ginger garlic paste");

    private final RecipeCatalog recipes;
    private final RecipeMatchingService matcher;
    private final RecipeFavoriteRepository favoriteRepo;
    private final InventoryBatchRepository batchRepo;
    private final InventoryOperations ops;
    private final MealPlanService mealPlans;
    private final UserService userService;
    private final CatalogService catalog;
    private final KitchenAuthGuard guard;
    private final AppClock clock;

    public RecipeService(RecipeCatalog recipes, RecipeMatchingService matcher, RecipeFavoriteRepository favoriteRepo,
                         InventoryBatchRepository batchRepo, InventoryOperations ops, MealPlanService mealPlans,
                         UserService userService, CatalogService catalog, KitchenAuthGuard guard, AppClock clock) {
        this.recipes = recipes;
        this.matcher = matcher;
        this.favoriteRepo = favoriteRepo;
        this.batchRepo = batchRepo;
        this.ops = ops;
        this.mealPlans = mealPlans;
        this.userService = userService;
        this.catalog = catalog;
        this.guard = guard;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ DTOs

    public record RecipeSummary(UUID id, String slug, String title, String description, String cuisine, RecipeDiet dietaryType,
                                int prepTimeMinutes, int cookTimeMinutes, int totalTimeMinutes, int servings,
                                Difficulty difficulty, String imageUrl, boolean favorite,
                                RecipeMatchingService.Readiness readiness, int matchPercent, double rankScore,
                                int missingCount, List<String> missing, List<String> usesExpiring) {
    }

    public record RecipeDetail(RecipeSummary summary, List<String> instructions, int servingsRequested,
                               List<RecipeMatchingService.IngredientMatch> ingredients) {
    }

    public record RecommendationFilter(String diet, Integer maxTime, boolean readyOnly, boolean expiringOnly,
                                       UUID ingredient, String q, boolean favoritesOnly, Integer limit) {
    }

    public enum CookMode { STRICT, AVAILABLE_ONLY }

    public record CookRequest(@Min(1) @Max(20) Integer servings, MealSlot mealSlot, CookMode mode) {
    }

    public record Deduction(UUID batchId, String name, BigDecimal deducted, Unit unit, BigDecimal remaining) {
    }

    public record Skipped(String ingredient, String reason) {
    }

    public record LowStock(UUID catalogItemId, String name, BigDecimal remaining, Unit unit) {
    }

    public record CookResult(String recipeTitle, int servings, List<Deduction> deductions, List<Skipped> skipped,
                             List<LowStock> lowStock, UUID mealPlanId) {
    }

    // ------------------------------------------------------------------ discovery

    @Transactional(readOnly = true)
    public List<RecipeSummary> recommendations(UUID kitchenId, UUID userId, RecommendationFilter f) {
        guard.requireMembership(kitchenId, userId);
        Map<UUID, List<RecipeMatchingService.Portion>> stock = stock(kitchenId);
        Set<UUID> favorites = favoriteIds(userId);
        Set<RecipeDiet> diets = allowedDiets(f.diet(), userId);
        boolean jain = isJain(f.diet(), userId);
        String q = f.q() == null ? null : f.q().trim().toLowerCase(Locale.ROOT);

        record Scored(RecipeCatalog.Entry entry, RecipeMatchingService.Match match) {
        }
        List<Scored> scored = new ArrayList<>();
        for (RecipeCatalog.Entry e : recipes.all()) {
            Recipe r = e.recipe();
            if (!diets.contains(r.getDietaryType()) || (jain && !jainFriendly(e))) {
                continue;
            }
            if (f.maxTime() != null && r.getPrepTimeMinutes() + r.getCookTimeMinutes() > f.maxTime()) {
                continue;
            }
            if (q != null && !q.isEmpty() && !r.getTitle().toLowerCase(Locale.ROOT).contains(q)
                    && !r.getCuisine().toLowerCase(Locale.ROOT).contains(q)
                    && e.ingredients().stream().noneMatch(i -> i.getIngredientName().toLowerCase(Locale.ROOT).contains(q))) {
                continue;
            }
            if (f.ingredient() != null && e.ingredients().stream().noneMatch(i -> f.ingredient().equals(i.getCatalogItemId()))) {
                continue;
            }
            if (f.favoritesOnly() && !favorites.contains(r.getId())) {
                continue;
            }
            RecipeMatchingService.Match m = matcher.match(e.ingredients(), 1.0, stock);
            if (f.readyOnly() && m.readiness() != RecipeMatchingService.Readiness.READY_TO_COOK) {
                continue;
            }
            if (f.expiringOnly() && m.usesExpiring().isEmpty()) {
                continue;
            }
            scored.add(new Scored(e, m));
        }
        Comparator<Scored> order = Comparator.comparing((Scored s) -> s.match(), RecipeMatchingService.ordering())
                .thenComparingInt(s -> s.entry().recipe().getPrepTimeMinutes() + s.entry().recipe().getCookTimeMinutes())
                .thenComparing(s -> s.entry().recipe().getTitle());
        int limit = f.limit() == null ? 100 : Math.max(1, Math.min(f.limit(), 200));
        return scored.stream().sorted(order).limit(limit)
                .map(s -> summary(s.entry().recipe(), s.match(), favorites.contains(s.entry().recipe().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RecipeDetail detail(UUID kitchenId, UUID userId, String idOrSlug, Integer servings) {
        guard.requireMembership(kitchenId, userId);
        RecipeCatalog.Entry e = recipes.byIdOrSlug(idOrSlug).orElseThrow(() -> ApiException.notFound("Recipe not found."));
        int serves = servings == null ? e.recipe().getServings() : Math.max(1, Math.min(servings, 20));
        RecipeMatchingService.Match m = matcher.match(e.ingredients(), (double) serves / e.recipe().getServings(), stock(kitchenId));
        boolean fav = favoriteRepo.findByUserIdAndRecipeId(userId, e.recipe().getId()).isPresent();
        return new RecipeDetail(summary(e.recipe(), m, fav), e.recipe().getInstructions(), serves, m.ingredients());
    }

    // ------------------------------------------------------------------ cooking

    /**
     * "I Cooked This": deducts tracked ingredients (scaled by servings) from active batches, earliest expiry first,
     * in one transaction with all affected rows locked. STRICT mode rejects with 409 and changes nothing if any
     * required ingredient is short; AVAILABLE_ONLY deducts what is there.
     */
    @Transactional
    public CookResult cook(UUID kitchenId, UUID userId, UUID recipeId, CookRequest req) {
        guard.requireMembership(kitchenId, userId);
        RecipeCatalog.Entry e = recipes.byId(recipeId).orElseThrow(() -> ApiException.notFound("Recipe not found."));
        Recipe recipe = e.recipe();
        int servings = req.servings() == null ? recipe.getServings() : req.servings();
        BigDecimal factor = BigDecimal.valueOf(servings).divide(BigDecimal.valueOf(recipe.getServings()), 6, RoundingMode.HALF_UP);
        CookMode mode = req.mode() == null ? CookMode.STRICT : req.mode();

        List<RecipeIngredient> tracked = e.ingredients().stream()
                .filter(i -> !i.isPantryStaple() && i.getCatalogItemId() != null).toList();
        Set<UUID> ids = tracked.stream().map(RecipeIngredient::getCatalogItemId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, List<InventoryBatch>> byItem = new HashMap<>();
        if (!ids.isEmpty()) {
            for (InventoryBatch b : batchRepo.lockActiveByCatalogItems(kitchenId, ids)) {
                byItem.computeIfAbsent(b.getCatalogItemId(), k -> new ArrayList<>()).add(b);
            }
        }
        byItem.values().forEach(list -> list.sort(Comparator.comparing(InventoryBatch::getExpiryDate)
                .thenComparing(InventoryBatch::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))));

        // 1) Plan every allocation before mutating anything.
        record Allocation(InventoryBatch batch, BigDecimal amountInBatchUnit) {
        }
        List<Allocation> plan = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        Map<String, List<String>> shortfalls = new LinkedHashMap<>();
        for (RecipeIngredient ing : tracked) {
            BigDecimal needed = ing.getQuantity().multiply(factor).setScale(2, RoundingMode.HALF_UP);
            List<InventoryBatch> batches = byItem.getOrDefault(ing.getCatalogItemId(), List.of());
            List<InventoryBatch> convertible = batches.stream().filter(b -> Units.compatible(b.getUnit(), ing.getUnit())).toList();
            if (batches.isEmpty()) {
                if (ing.isOptional()) {
                    skipped.add(new Skipped(ing.getIngredientName(), "optional, not in kitchen"));
                } else {
                    shortfalls.put(ing.getIngredientName(), List.of("Not in your kitchen."));
                    skipped.add(new Skipped(ing.getIngredientName(), "not in kitchen"));
                }
                continue;
            }
            if (convertible.isEmpty()) {
                skipped.add(new Skipped(ing.getIngredientName(), "tracked in a different unit - update it manually"));
                continue;
            }
            BigDecimal remainingNeed = needed;
            for (InventoryBatch b : convertible) {
                if (remainingNeed.signum() <= 0) {
                    break;
                }
                BigDecimal haveInRecipeUnit = Units.convert(b.getCurrentQuantity(), b.getUnit(), ing.getUnit()).orElseThrow();
                BigDecimal take = haveInRecipeUnit.min(remainingNeed);
                BigDecimal inBatchUnit = Units.convert(take, ing.getUnit(), b.getUnit()).orElseThrow()
                        .setScale(2, RoundingMode.HALF_UP).min(b.getCurrentQuantity());
                if (inBatchUnit.signum() > 0) {
                    plan.add(new Allocation(b, inBatchUnit));
                }
                remainingNeed = remainingNeed.subtract(take);
            }
            if (remainingNeed.compareTo(new BigDecimal("0.01")) >= 0) {
                if (ing.isOptional()) {
                    skipped.add(new Skipped(ing.getIngredientName(), "optional, only partly available"));
                } else {
                    BigDecimal have = needed.subtract(remainingNeed);
                    shortfalls.put(ing.getIngredientName(), List.of("Need " + plain(needed) + " " + ing.getUnit().dbValue()
                            + ", have " + plain(have) + " " + ing.getUnit().dbValue() + "."));
                }
            }
        }
        if (mode == CookMode.STRICT && !shortfalls.isEmpty()) {
            throw ApiException.conflict("Not enough stock to cook " + recipe.getTitle() + ". Nothing was deducted.", shortfalls);
        }

        // 2) Execute: each deduction writes its batch update and ledger event in this same transaction.
        List<Deduction> deductions = new ArrayList<>();
        Set<UUID> touched = new LinkedHashSet<>();
        for (Allocation a : plan) {
            InventoryBatch b = ops.deduct(a.batch(), a.amountInBatchUnit(), userId, recipe.getId(), "Cooked " + recipe.getTitle());
            deductions.add(new Deduction(b.getId(), b.getCustomItemName(), a.amountInBatchUnit(), b.getUnit(), b.getCurrentQuantity()));
            touched.add(b.getCatalogItemId());
        }

        // 3) Low-stock prompt (US-03): remaining <= 25% of the largest batch bought, or nothing left.
        List<LowStock> low = new ArrayList<>();
        for (UUID itemId : touched) {
            List<InventoryBatch> batches = byItem.getOrDefault(itemId, List.of());
            InventoryBatch ref = batches.stream().max(Comparator.comparing(InventoryBatch::getInitialQuantity)).orElseThrow();
            BigDecimal remaining = batches.stream().filter(b -> b.getStatus() == BatchStatus.ACTIVE)
                    .map(b -> Units.convert(b.getCurrentQuantity(), b.getUnit(), ref.getUnit()).orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (remaining.compareTo(ref.getInitialQuantity().multiply(new BigDecimal("0.25"))) <= 0) {
                String name = catalog.item(itemId).map(i -> i.nameEn()).orElse(ref.getCustomItemName());
                low.add(new LowStock(itemId, name, remaining.setScale(2, RoundingMode.HALF_UP), ref.getUnit()));
            }
        }

        MealSlot slot = req.mealSlot() != null ? req.mealSlot() : slotFor(clock.timeOfDay());
        UUID planId = mealPlans.recordCooked(kitchenId, userId, clock.today(), slot, recipe.getId());
        return new CookResult(recipe.getTitle(), servings, deductions, skipped, low, planId);
    }

    // ------------------------------------------------------------------ favourites

    @Transactional
    public boolean setFavorite(UUID kitchenId, UUID userId, UUID recipeId, boolean favorite) {
        guard.requireMembership(kitchenId, userId);
        recipes.byId(recipeId).orElseThrow(() -> ApiException.notFound("Recipe not found."));
        var existing = favoriteRepo.findByUserIdAndRecipeId(userId, recipeId);
        if (favorite && existing.isEmpty()) {
            RecipeFavorite f = new RecipeFavorite();
            f.setUserId(userId);
            f.setRecipeId(recipeId);
            favoriteRepo.save(f);
        } else if (!favorite) {
            existing.ifPresent(favoriteRepo::delete);
        }
        return favorite;
    }

    // ------------------------------------------------------------------ helpers

    Map<UUID, List<RecipeMatchingService.Portion>> stock(UUID kitchenId) {
        LocalDate today = clock.today();
        Map<UUID, List<RecipeMatchingService.Portion>> stock = new HashMap<>();
        for (InventoryBatch b : batchRepo.findByKitchenIdAndStatus(kitchenId, BatchStatus.ACTIVE)) {
            if (b.isLeftover() || b.getCatalogItemId() == null) {
                continue;
            }
            stock.computeIfAbsent(b.getCatalogItemId(), k -> new ArrayList<>()).add(new RecipeMatchingService.Portion(
                    b.getCurrentQuantity(), b.getUnit(), ExpiryClassifier.classify(b.getExpiryDate(), today)));
        }
        return stock;
    }

    private Set<UUID> favoriteIds(UUID userId) {
        return favoriteRepo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(RecipeFavorite::getRecipeId).collect(Collectors.toSet());
    }

    private Set<RecipeDiet> allowedDiets(String diet, UUID userId) {
        if ("all".equalsIgnoreCase(diet)) {
            return EnumSet.allOf(RecipeDiet.class);
        }
        DietaryPreference pref = diet == null || diet.isBlank()
                ? userService.getOrCreatePreferences(userId).getDietaryType()
                : DietaryPreference.fromValue(diet.equalsIgnoreCase("non-vegetarian") ? "omnivore" : diet);
        return switch (pref) {
            case VEGAN -> EnumSet.of(RecipeDiet.VEGAN);
            case VEGETARIAN, JAIN -> EnumSet.of(RecipeDiet.VEGETARIAN, RecipeDiet.VEGAN);
            case EGGETARIAN -> EnumSet.of(RecipeDiet.VEGETARIAN, RecipeDiet.VEGAN, RecipeDiet.EGGETARIAN);
            case OMNIVORE -> EnumSet.allOf(RecipeDiet.class);
        };
    }

    private boolean isJain(String diet, UUID userId) {
        if (diet != null && !diet.isBlank()) {
            return "jain".equalsIgnoreCase(diet);
        }
        return userService.getOrCreatePreferences(userId).getDietaryType() == DietaryPreference.JAIN;
    }

    private static boolean jainFriendly(RecipeCatalog.Entry e) {
        return e.ingredients().stream().filter(i -> !i.isOptional())
                .noneMatch(i -> JAIN_EXCLUDED.contains(i.getIngredientName().toLowerCase(Locale.ROOT)));
    }

    static MealSlot slotFor(LocalTime t) {
        if (t.isBefore(LocalTime.of(11, 0))) {
            return MealSlot.BREAKFAST;
        }
        if (t.isBefore(LocalTime.of(16, 0))) {
            return MealSlot.LUNCH;
        }
        if (t.isBefore(LocalTime.of(18, 30))) {
            return MealSlot.SNACK;
        }
        return MealSlot.DINNER;
    }

    private static RecipeSummary summary(Recipe r, RecipeMatchingService.Match m, boolean favorite) {
        return new RecipeSummary(r.getId(), r.getSlug(), r.getTitle(), r.getDescription(), r.getCuisine(), r.getDietaryType(),
                r.getPrepTimeMinutes(), r.getCookTimeMinutes(), r.getPrepTimeMinutes() + r.getCookTimeMinutes(), r.getServings(),
                r.getDifficulty(), r.getImageUrl(), favorite, m.readiness(), m.matchPercent(), m.rankScore(), m.missingCount(),
                m.missing(), m.usesExpiring());
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
