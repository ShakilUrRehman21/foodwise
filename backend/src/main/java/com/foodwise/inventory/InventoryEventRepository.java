package com.foodwise.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryEventRepository extends JpaRepository<InventoryEvent, UUID> {

    List<InventoryEvent> findByBatchIdOrderByCreatedAtDesc(UUID batchId);
}
