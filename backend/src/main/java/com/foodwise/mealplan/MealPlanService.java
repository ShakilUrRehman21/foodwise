package com.foodwise.mealplan;

import com.foodwise.common.ApiException;
import com.foodwise.common.AppClock;
import com.foodwise.common.enums.MealPlanStatus;
import com.foodwise.common.enums.MealSlot;
import com.foodwise.recipe.Recipe;
import com.foodwise.recipe.RecipeRepository;
import com.foodwise.security.KitchenAuthGuard;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Weekly meal planning (Roadmap 6.B4). One entry per kitchen, date and meal slot. */
@Service
public class MealPlanService {

    private final MealPlanRepository repo;
    private final RecipeRepository recipeRepo;
    private final KitchenAuthGuard guard;
    private final AppClock clock;

    public MealPlanService(MealPlanRepository repo, RecipeRepository recipeRepo, KitchenAuthGuard guard, AppClock clock) {
        this.repo = repo;
        this.recipeRepo = recipeRepo;
        this.guard = guard;
        this.clock = clock;
    }

    public record MealPlanDto(UUID id, LocalDate planDate, MealSlot mealSlot, UUID recipeId, String recipeSlug, String title,
                              String notes, MealPlanStatus status) {
    }

    public record UpsertRequest(@NotNull LocalDate planDate, @NotNull MealSlot mealSlot, UUID recipeId,
                                @Size(max = 150) String customMealTitle, @Size(max = 500) String notes) {
    }

    public record StatusRequest(@NotNull MealPlanStatus status) {
    }

    /** {@code week} as ISO week (2026-W45) or {@code start} date; defaults to the current week (Mon-Sun). */
    @Transactional(readOnly = true)
    public List<MealPlanDto> week(UUID kitchenId, UUID userId, String week, LocalDate start, Integer days) {
        guard.requireMembership(kitchenId, userId);
        LocalDate from;
        if (week != null && !week.isBlank()) {
            try {
                String[] p = week.trim().toUpperCase().split("-W");
                from = LocalDate.of(Integer.parseInt(p[0]), 1, 4)
                        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, Integer.parseInt(p[1]))
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            } catch (RuntimeException e) {
                throw ApiException.validation("week", "Use the ISO format, e.g. 2026-W45.");
            }
        } else {
            from = start != null ? start : clock.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        int span = days == null ? 7 : Math.max(1, Math.min(days, 31));
        List<MealPlan> plans = repo.findByKitchenIdAndPlanDateBetweenOrderByPlanDateAscMealSlotAsc(kitchenId, from, from.plusDays(span - 1));
        Map<UUID, Recipe> recipes = recipeRepo.findAllById(plans.stream().map(MealPlan::getRecipeId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Recipe::getId, Function.identity()));
        return plans.stream()
                .sorted(Comparator.comparing(MealPlan::getPlanDate).thenComparing(p -> p.getMealSlot().ordinal()))
                .map(p -> toDto(p, recipes.get(p.getRecipeId())))
                .toList();
    }

    @Transactional
    public MealPlanDto upsert(UUID kitchenId, UUID userId, UpsertRequest req) {
        guard.requireMembership(kitchenId, userId);
        Recipe recipe = null;
        if (req.recipeId() != null) {
            recipe = recipeRepo.findById(req.recipeId()).orElseThrow(() -> ApiException.validation("recipeId", "Unknown recipe."));
        } else if (req.customMealTitle() == null || req.customMealTitle().isBlank()) {
            throw ApiException.validation("customMealTitle", "Choose a recipe or type what you plan to eat.");
        }
        MealPlan plan = repo.findByKitchenIdAndPlanDateAndMealSlot(kitchenId, req.planDate(), req.mealSlot()).orElseGet(MealPlan::new);
        plan.setKitchenId(kitchenId);
        plan.setUserId(userId);
        plan.setPlanDate(req.planDate());
        plan.setMealSlot(req.mealSlot());
        plan.setRecipeId(recipe == null ? null : recipe.getId());
        plan.setCustomMealTitle(recipe == null ? req.customMealTitle().trim() : null);
        plan.setNotes(req.notes());
        if (plan.getStatus() == null) {
            plan.setStatus(MealPlanStatus.PLANNED);
        }
        return toDto(repo.save(plan), recipe);
    }

    @Transactional
    public MealPlanDto setStatus(UUID kitchenId, UUID userId, UUID planId, StatusRequest req) {
        guard.requireMembership(kitchenId, userId);
        MealPlan plan = repo.findByIdAndKitchenId(planId, kitchenId).orElseThrow(() -> ApiException.notFound("Meal plan not found."));
        plan.setStatus(req.status());
        return toDto(repo.save(plan), plan.getRecipeId() == null ? null : recipeRepo.findById(plan.getRecipeId()).orElse(null));
    }

    @Transactional
    public void delete(UUID kitchenId, UUID userId, UUID planId) {
        guard.requireMembership(kitchenId, userId);
        repo.delete(repo.findByIdAndKitchenId(planId, kitchenId).orElseThrow(() -> ApiException.notFound("Meal plan not found.")));
    }

    /** Called inside the cook transaction: mark the slot as cooked with this recipe. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID recordCooked(UUID kitchenId, UUID userId, LocalDate date, MealSlot slot, UUID recipeId) {
        MealPlan plan = repo.findByKitchenIdAndPlanDateAndMealSlot(kitchenId, date, slot).orElseGet(MealPlan::new);
        plan.setKitchenId(kitchenId);
        plan.setUserId(userId);
        plan.setPlanDate(date);
        plan.setMealSlot(slot);
        plan.setRecipeId(recipeId);
        plan.setCustomMealTitle(null);
        plan.setStatus(MealPlanStatus.COOKED);
        return repo.save(plan).getId();
    }

    private MealPlanDto toDto(MealPlan p, Recipe r) {
        return new MealPlanDto(p.getId(), p.getPlanDate(), p.getMealSlot(), p.getRecipeId(), r == null ? null : r.getSlug(),
                r != null ? r.getTitle() : p.getCustomMealTitle(), p.getNotes(), p.getStatus());
    }
}
