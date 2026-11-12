package com.foodwise.shopping;

import com.foodwise.common.ApiResponse;
import com.foodwise.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchens/{kitchenId}/shopping")
public class ShoppingController {

    private final ShoppingService shopping;

    public ShoppingController(ShoppingService shopping) {
        this.shopping = shopping;
    }

    public record FromRecipeRequest(@NotEmpty @Size(max = 50) List<UUID> catalogItemIds) {
    }

    @GetMapping
    public ApiResponse<ShoppingService.ListDto> get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(shopping.getActive(kitchenId, user.id()));
    }

    @PostMapping("/items")
    public ApiResponse<ShoppingService.AddResult> add(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                      @Valid @RequestBody ShoppingService.AddItemRequest req) {
        return ApiResponse.ok(shopping.addItems(kitchenId, user.id(), List.of(req)));
    }

    @PostMapping("/items/bulk")
    public ApiResponse<ShoppingService.AddResult> addBulk(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                          @Valid @RequestBody ShoppingService.AddItemsRequest req) {
        return ApiResponse.ok(shopping.addItems(kitchenId, user.id(), req.items()));
    }

    @PostMapping("/from-recipe/{recipeId}")
    public ApiResponse<ShoppingService.AddResult> fromRecipe(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                             @PathVariable UUID recipeId, @Valid @RequestBody FromRecipeRequest req) {
        return ApiResponse.ok(shopping.addMissingFromRecipe(kitchenId, user.id(), recipeId, req.catalogItemIds()));
    }

    @PostMapping("/low-stock")
    public ApiResponse<ShoppingService.AddResult> lowStock(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(shopping.addLowStock(kitchenId, user.id()));
    }

    @PatchMapping("/items/{itemId}")
    public ApiResponse<ShoppingService.ListDto> update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                       @PathVariable UUID itemId, @Valid @RequestBody ShoppingService.UpdateItemRequest req) {
        return ApiResponse.ok(shopping.updateItem(kitchenId, user.id(), itemId, req));
    }

    @PatchMapping("/items/{itemId}/check")
    public ApiResponse<ShoppingService.ListDto> check(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                      @PathVariable UUID itemId,
                                                      @RequestBody(required = false) ShoppingService.CheckRequest req) {
        return ApiResponse.ok(shopping.check(kitchenId, user.id(), itemId, req));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<ShoppingService.ListDto> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                       @PathVariable UUID itemId) {
        return ApiResponse.ok(shopping.deleteItem(kitchenId, user.id(), itemId));
    }

    @PostMapping("/clear-checked")
    public ApiResponse<ShoppingService.ListDto> clearChecked(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(shopping.clearChecked(kitchenId, user.id()));
    }

    @PostMapping("/{listId}/import-to-inventory")
    public ApiResponse<ShoppingService.ImportResult> importToInventory(@AuthenticationPrincipal CurrentUser user,
                                                                       @PathVariable UUID kitchenId, @PathVariable UUID listId,
                                                                       @Valid @RequestBody ShoppingService.ImportRequest req) {
        ShoppingService.ImportResult result = shopping.importToInventory(kitchenId, user.id(), listId, req);
        return ApiResponse.ok(result, "Added " + result.created().size() + " items to your kitchen.");
    }
}
