package com.foodwise.inventory;

import com.foodwise.common.enums.Unit;
import com.foodwise.common.enums.WasteReason;
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

/** JPA entity for table {@code food_waste_logs} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "food_waste_logs")
public class FoodWasteLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kitchen_id")
    private UUID kitchenId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "quantity_wasted")
    private BigDecimal quantityWasted;

    @Column(name = "unit")
    private Unit unit;

    @Column(name = "estimated_loss_inr")
    private BigDecimal estimatedLossInr;

    @Column(name = "waste_reason")
    private WasteReason wasteReason;

    @CreationTimestamp
    @Column(name = "logged_at", nullable = false, updatable = false)
    private Instant loggedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getKitchenId() {
        return kitchenId;
    }

    public void setKitchenId(UUID kitchenId) {
        this.kitchenId = kitchenId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getQuantityWasted() {
        return quantityWasted;
    }

    public void setQuantityWasted(BigDecimal quantityWasted) {
        this.quantityWasted = quantityWasted;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public BigDecimal getEstimatedLossInr() {
        return estimatedLossInr;
    }

    public void setEstimatedLossInr(BigDecimal estimatedLossInr) {
        this.estimatedLossInr = estimatedLossInr;
    }

    public WasteReason getWasteReason() {
        return wasteReason;
    }

    public void setWasteReason(WasteReason wasteReason) {
        this.wasteReason = wasteReason;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(Instant loggedAt) {
        this.loggedAt = loggedAt;
    }
}
