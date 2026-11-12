package com.foodwise.mealplan;

import com.foodwise.common.ApiResponse;
import com.foodwise.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchens/{kitchenId}/meal-plans")
public class MealPlanController {

    private final MealPlanService mealPlans;

    public MealPlanController(MealPlanService mealPlans) {
        this.mealPlans = mealPlans;
    }

    @GetMapping
    public ApiResponse<List<MealPlanService.MealPlanDto>> week(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                               @RequestParam(required = false) String week,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                               @RequestParam(required = false) Integer days) {
        return ApiResponse.ok(mealPlans.week(kitchenId, user.id(), week, start, days));
    }

    @PostMapping
    public ApiResponse<MealPlanService.MealPlanDto> upsert(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                           @Valid @RequestBody MealPlanService.UpsertRequest req) {
        return ApiResponse.ok(mealPlans.upsert(kitchenId, user.id(), req), "Meal planned.");
    }

    @PatchMapping("/{planId}/status")
    public ApiResponse<MealPlanService.MealPlanDto> status(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                           @PathVariable UUID planId,
                                                           @Valid @RequestBody MealPlanService.StatusRequest req) {
        return ApiResponse.ok(mealPlans.setStatus(kitchenId, user.id(), planId, req));
    }

    @DeleteMapping("/{planId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId, @PathVariable UUID planId) {
        mealPlans.delete(kitchenId, user.id(), planId);
        return ApiResponse.ok(null, "Removed from plan.");
    }
}
