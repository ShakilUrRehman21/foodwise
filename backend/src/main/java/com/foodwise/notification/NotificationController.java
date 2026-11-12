package com.foodwise.notification;

import com.foodwise.common.ApiResponse;
import com.foodwise.common.enums.NotificationStatus;
import com.foodwise.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchens/{kitchenId}/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<NotificationService.NotificationPage> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                                  @RequestParam(required = false) NotificationStatus status,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notifications.list(kitchenId, user.id(), status, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unread(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(Map.of("count", notifications.unreadCount(kitchenId, user.id())));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationService.NotificationDto> read(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                                 @PathVariable UUID notificationId) {
        return ApiResponse.ok(notifications.markRead(kitchenId, user.id(), notificationId));
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> readAll(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(Map.of("updated", notifications.markAllRead(kitchenId, user.id())));
    }
}
