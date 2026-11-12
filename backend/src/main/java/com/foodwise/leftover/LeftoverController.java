package com.foodwise.leftover;

import com.foodwise.common.ApiResponse;
import com.foodwise.inventory.BatchDto;
import com.foodwise.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchens/{kitchenId}/leftovers")
public class LeftoverController {

    private final LeftoverService leftovers;

    public LeftoverController(LeftoverService leftovers) {
        this.leftovers = leftovers;
    }

    @GetMapping
    public ApiResponse<List<LeftoverService.LeftoverDto>> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(leftovers.list(kitchenId, user.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LeftoverService.LeftoverDto> log(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                        @Valid @RequestBody LeftoverService.LogLeftoverRequest req) {
        return ApiResponse.ok(leftovers.log(kitchenId, user.id(), req), "Leftover saved.");
    }

    @GetMapping("/{batchId}/repurpose")
    public ApiResponse<List<LeftoverService.RepurposeIdea>> repurpose(@AuthenticationPrincipal CurrentUser user,
                                                                      @PathVariable UUID kitchenId, @PathVariable UUID batchId) {
        return ApiResponse.ok(leftovers.repurpose(kitchenId, user.id(), batchId));
    }

    @PatchMapping("/{batchId}/consume")
    public ApiResponse<BatchDto> consume(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                         @PathVariable UUID batchId,
                                         @Valid @RequestBody(required = false) LeftoverService.ConsumeRequest req) {
        return ApiResponse.ok(leftovers.consume(kitchenId, user.id(), batchId, req), "Enjoy your meal!");
    }

    @PostMapping("/{batchId}/discard")
    public ApiResponse<BatchDto> discard(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                         @PathVariable UUID batchId) {
        return ApiResponse.ok(leftovers.discard(kitchenId, user.id(), batchId), "Logged as waste.");
    }
}
