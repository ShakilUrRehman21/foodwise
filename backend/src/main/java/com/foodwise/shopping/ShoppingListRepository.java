package com.foodwise.shopping;

import com.foodwise.common.enums.ShoppingListStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {

    Optional<ShoppingList> findFirstByKitchenIdAndStatus(UUID kitchenId, ShoppingListStatus status);

    Optional<ShoppingList> findByIdAndKitchenId(UUID id, UUID kitchenId);

    /** Race-safe "create if none": uq_shopping_lists_one_active guarantees a single active list per kitchen. */
    @Modifying
    @Query(value = """
            INSERT INTO shopping_lists (id, kitchen_id, name, status, created_at, updated_at)
            VALUES (gen_random_uuid(), :kitchenId, 'Current Shopping List', 'active', now(), now())
            ON CONFLICT (kitchen_id) WHERE status = 'active' DO NOTHING
            """, nativeQuery = true)
    int insertActiveIfAbsent(@Param("kitchenId") UUID kitchenId);
}
