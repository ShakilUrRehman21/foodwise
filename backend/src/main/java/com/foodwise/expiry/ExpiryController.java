package com.foodwise.expiry;

import com.foodwise.common.ApiResponse;
import com.foodwise.inventory.BatchDto;
import com.foodwise.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchens/{kitchenId}/expiry")
public class ExpiryController {

    private final ExpiryService expiry;

    public ExpiryController(ExpiryService expiry) {
        this.expiry = expiry;
    }

    @GetMapping("/timeline")
    public ApiResponse<ExpiryService.Timeline> timeline(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(expiry.timeline(kitchenId, user.id()));
    }

    @GetMapping("/urgent")
    public ApiResponse<List<BatchDto>> urgent(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(expiry.urgent(kitchenId, user.id()));
    }
}
