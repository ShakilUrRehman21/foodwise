package com.foodwise.analytics;

import com.foodwise.common.ApiResponse;
import com.foodwise.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchens/{kitchenId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/monthly-summary")
    public ApiResponse<AnalyticsService.MonthlySummary> summary(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                                @RequestParam(required = false) String month) {
        return ApiResponse.ok(analytics.monthlySummary(kitchenId, user.id(), analytics.parseMonth(month)));
    }

    @GetMapping("/waste-breakdown")
    public ApiResponse<List<AnalyticsService.CategorySlice>> breakdown(@AuthenticationPrincipal CurrentUser user,
                                                                       @PathVariable UUID kitchenId,
                                                                       @RequestParam(required = false) String month) {
        return ApiResponse.ok(analytics.wasteBreakdown(kitchenId, user.id(), analytics.parseMonth(month)));
    }

    @GetMapping("/top-wasted")
    public ApiResponse<List<AnalyticsService.TopWasted>> topWasted(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                                   @RequestParam(required = false) String month,
                                                                   @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(analytics.topWasted(kitchenId, user.id(), analytics.parseMonth(month), limit));
    }
}
