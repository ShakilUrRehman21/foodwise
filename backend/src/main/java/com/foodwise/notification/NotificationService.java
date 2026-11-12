package com.foodwise.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodwise.common.ApiException;
import com.foodwise.common.enums.NotificationStatus;
import com.foodwise.common.enums.NotificationType;
import com.foodwise.security.KitchenAuthGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-app notifications (always on). Push/email channels are future work (Roadmap UD-005). */
@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final KitchenAuthGuard guard;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository repo, KitchenAuthGuard guard, ObjectMapper objectMapper) {
        this.repo = repo;
        this.guard = guard;
        this.objectMapper = objectMapper;
    }

    public record NotificationDto(UUID id, NotificationType type, String title, String message, NotificationStatus status,
                                  String actionUrl, Map<String, Object> metadata, Instant createdAt, Instant readAt) {
    }

    public record NotificationPage(List<NotificationDto> items, long unreadCount, int page, int size, long total) {
    }

    /**
     * Creates a notification at most once per (user, dedupeKey).
     *
     * @return true if a new row was inserted
     */
    @Transactional
    public boolean notifyOnce(UUID userId, UUID kitchenId, NotificationType type, String title, String message,
                              String actionUrl, String dedupeKey, Map<String, Object> metadata) {
        Map<String, Object> meta = new HashMap<>(metadata == null ? Map.of() : metadata);
        meta.put("dedupe_key", dedupeKey);
        try {
            return repo.insertDeduplicated(userId, kitchenId, type.dbValue(), truncate(title, 150), message,
                    truncate(actionUrl, 255), objectMapper.writeValueAsString(meta)) == 1;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public NotificationPage list(UUID kitchenId, UUID userId, NotificationStatus status, int page, int size) {
        guard.requireMembership(kitchenId, userId);
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<Notification> result = status == null
                ? repo.findByUserIdAndKitchenIdOrderByCreatedAtDesc(userId, kitchenId, pr)
                : repo.findByUserIdAndKitchenIdAndStatusOrderByCreatedAtDesc(userId, kitchenId, status, pr);
        long unread = repo.countByUserIdAndKitchenIdAndStatus(userId, kitchenId, NotificationStatus.UNREAD);
        return new NotificationPage(result.map(this::toDto).getContent(), unread, pr.getPageNumber(), pr.getPageSize(),
                result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        return repo.countByUserIdAndKitchenIdAndStatus(userId, kitchenId, NotificationStatus.UNREAD);
    }

    @Transactional
    public NotificationDto markRead(UUID kitchenId, UUID userId, UUID notificationId) {
        guard.requireMembership(kitchenId, userId);
        Notification n = repo.findById(notificationId)
                .filter(x -> x.getUserId().equals(userId) && x.getKitchenId().equals(kitchenId))
                .orElseThrow(() -> ApiException.notFound("Notification not found."));
        if (n.getStatus() == NotificationStatus.UNREAD) {
            n.setStatus(NotificationStatus.READ);
            n.setReadAt(Instant.now());
            n = repo.save(n);
        }
        return toDto(n);
    }

    @Transactional
    public int markAllRead(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        return repo.markAllRead(userId, kitchenId, Instant.now());
    }

    private NotificationDto toDto(Notification n) {
        Map<String, Object> meta = n.getMetadata() == null ? null : new HashMap<>(n.getMetadata());
        if (meta != null) {
            meta.remove("dedupe_key");
        }
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getStatus(), n.getActionUrl(),
                meta, n.getCreatedAt(), n.getReadAt());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
