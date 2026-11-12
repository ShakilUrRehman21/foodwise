package com.foodwise.kitchen;

import com.foodwise.common.enums.KitchenRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitchenMemberRepository extends JpaRepository<KitchenMember, UUID> {

    Optional<KitchenMember> findByKitchenIdAndUserId(UUID kitchenId, UUID userId);

    List<KitchenMember> findByKitchenIdOrderByJoinedAt(UUID kitchenId);

    List<KitchenMember> findByUserId(UUID userId);

    long countByKitchenIdAndRole(UUID kitchenId, KitchenRole role);

    long countByKitchenId(UUID kitchenId);
}
