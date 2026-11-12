package com.foodwise.notification;

import com.foodwise.common.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndKitchenIdOrderByCreatedAtDesc(UUID userId, UUID kitchenId, Pageable pageable);

    Page<Notification> findByUserIdAndKitchenIdAndStatusOrderByCreatedAtDesc(UUID userId, UUID kitchenId,
                                                                             NotificationStatus status, Pageable pageable);

    long countByUserIdAndKitchenIdAndStatus(UUID userId, UUID kitchenId, NotificationStatus status);

    @Modifying
    @Query("update Notification n set n.status = :to, n.readAt = :now "
            + "where n.userId = :userId and n.kitchenId = :kitchenId and n.status = :from")
    int updateStatus(UUID userId, UUID kitchenId, NotificationStatus from, NotificationStatus to, Instant now);

    default int markAllRead(UUID userId, UUID kitchenId, Instant now) {
        return updateStatus(userId, kitchenId, NotificationStatus.UNREAD, NotificationStatus.READ, now);
    }

    /**
     * Idempotent insert keyed by {@code metadata.dedupe_key} (unique partial index uq_notifications_dedupe), so
     * scheduler re-runs, restarts or multiple instances never create duplicates.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notifications (id, user_id, kitchen_id, type, title, message, status, action_url, metadata, created_at)
            VALUES (gen_random_uuid(), :userId, :kitchenId, :type, :title, :message, 'unread', :actionUrl,
                    CAST(:metadata AS jsonb), now())
            ON CONFLICT (user_id, (metadata ->> 'dedupe_key')) WHERE (metadata ->> 'dedupe_key') IS NOT NULL DO NOTHING
            """, nativeQuery = true)
    int insertDeduplicated(@Param("userId") UUID userId, @Param("kitchenId") UUID kitchenId, @Param("type") String type,
                           @Param("title") String title, @Param("message") String message,
                           @Param("actionUrl") String actionUrl, @Param("metadata") String metadataJson);
}
