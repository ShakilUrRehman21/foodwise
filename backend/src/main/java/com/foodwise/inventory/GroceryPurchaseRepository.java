package com.foodwise.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GroceryPurchaseRepository extends JpaRepository<GroceryPurchase, UUID> {
}
