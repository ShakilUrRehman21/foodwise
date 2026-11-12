package com.foodwise.inventory;

import com.foodwise.common.enums.InventoryEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** JPA entity for table {@code inventory_events} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "inventory_events")
public class InventoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "kitchen_id")
    private UUID kitchenId;

    @Column(name = "event_type")
    private InventoryEventType eventType;

    @Column(name = "quantity_delta")
    private BigDecimal quantityDelta;

    @Column(name = "quantity_after")
    private BigDecimal quantityAfter;

    @Column(name = "reason")
    private String reason;

    @Column(name = "recipe_id")
    private UUID recipeId;

    @Column(name = "waste_log_id")
    private UUID wasteLogId;

    @Column(name = "performed_by_user_id")
    private UUID performedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public UUID getKitchenId() {
        return kitchenId;
    }

    public void setKitchenId(UUID kitchenId) {
        this.kitchenId = kitchenId;
    }

    public InventoryEventType getEventType() {
        return eventType;
    }

    public void setEventType(InventoryEventType eventType) {
        this.eventType = eventType;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public void setQuantityDelta(BigDecimal quantityDelta) {
        this.quantityDelta = quantityDelta;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public void setQuantityAfter(BigDecimal quantityAfter) {
        this.quantityAfter = quantityAfter;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(UUID recipeId) {
        this.recipeId = recipeId;
    }

    public UUID getWasteLogId() {
        return wasteLogId;
    }

    public void setWasteLogId(UUID wasteLogId) {
        this.wasteLogId = wasteLogId;
    }

    public UUID getPerformedByUserId() {
        return performedByUserId;
    }

    public void setPerformedByUserId(UUID performedByUserId) {
        this.performedByUserId = performedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
