package com.foodwise.shopping;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {

    List<ShoppingListItem> findByShoppingListIdOrderByCreatedAt(UUID listId);

    Optional<ShoppingListItem> findByIdAndShoppingListId(UUID id, UUID listId);

    long countByShoppingListId(UUID listId);
}
