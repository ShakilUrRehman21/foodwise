package com.foodwise.recipe;

import com.foodwise.common.ApiResponse;
import com.foodwise.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchens/{kitchenId}/recipes")
public class RecipeController {

    private final RecipeService recipes;

    public RecipeController(RecipeService recipes) {
        this.recipes = recipes;
    }

    /**
     * @param diet one of vegetarian, vegan, eggetarian, jain, omnivore, all (default: the user's preference)
     */
    @GetMapping("/recommendations")
    public ApiResponse<List<RecipeService.RecipeSummary>> recommendations(
            @AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
            @RequestParam(required = false) String diet,
            @RequestParam(required = false) Integer maxCookTime,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) UUID ingredient,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {
        String f = filter == null ? "" : filter.toUpperCase();
        return ApiResponse.ok(recipes.recommendations(kitchenId, user.id(), new RecipeService.RecommendationFilter(
                diet, maxCookTime, f.equals("READY_ONLY"), f.equals("EXPIRING"), ingredient, q, f.equals("FAVORITES"), limit)));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<RecipeService.RecipeSummary>> favorites(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(recipes.recommendations(kitchenId, user.id(),
                new RecipeService.RecommendationFilter("all", null, false, false, null, null, true, null)));
    }

    @GetMapping("/{idOrSlug}")
    public ApiResponse<RecipeService.RecipeDetail> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                          @PathVariable String idOrSlug,
                                                          @RequestParam(required = false) Integer servings) {
        return ApiResponse.ok(recipes.detail(kitchenId, user.id(), idOrSlug, servings));
    }

    @PostMapping("/{recipeId}/cook")
    public ApiResponse<RecipeService.CookResult> cook(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                      @PathVariable UUID recipeId,
                                                      @Valid @RequestBody(required = false) RecipeService.CookRequest req) {
        RecipeService.CookRequest body = req == null ? new RecipeService.CookRequest(null, null, null) : req;
        return ApiResponse.ok(recipes.cook(kitchenId, user.id(), recipeId, body), "Inventory updated. Nice cooking!");
    }

    @PostMapping("/{recipeId}/favorite")
    public ApiResponse<Map<String, Boolean>> favorite(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                      @PathVariable UUID recipeId) {
        return ApiResponse.ok(Map.of("favorite", recipes.setFavorite(kitchenId, user.id(), recipeId, true)));
    }

    @DeleteMapping("/{recipeId}/favorite")
    public ApiResponse<Map<String, Boolean>> unfavorite(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                        @PathVariable UUID recipeId) {
        return ApiResponse.ok(Map.of("favorite", recipes.setFavorite(kitchenId, user.id(), recipeId, false)));
    }
}
