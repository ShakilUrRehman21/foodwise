package com.foodwise.dashboard;

import com.foodwise.common.ApiResponse;
import com.foodwise.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/v1/kitchens/{kitchenId}/dashboard/pulse")
    public ApiResponse<DashboardService.Pulse> pulse(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(dashboard.pulse(kitchenId, user.id()));
    }
}
