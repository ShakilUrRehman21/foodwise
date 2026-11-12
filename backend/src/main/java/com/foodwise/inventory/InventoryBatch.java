package com.foodwise.inventory;

import com.foodwise.common.enums.BatchStatus;
import com.foodwise.common.enums.LeftoverType;
import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** JPA entity for table {@code inventory_batches} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "inventory_batches")
public class InventoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kitchen_id")
    private UUID kitchenId;

    @Column(name = "catalog_item_id")
    private UUID catalogItemId;

    @Column(name = "custom_item_name")
    private String customItemName;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "storage_location")
    private StorageLocation storageLocation;

    @Column(name = "initial_quantity")
    private BigDecimal initialQuantity;

    @Column(name = "current_quantity")
    private BigDecimal currentQuantity;

    @Column(name = "unit")
    private Unit unit;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "cost_inr")
    private BigDecimal costInr;

    @Column(name = "is_leftover")
    private boolean leftover;

    @Column(name = "cooked_at")
    private Instant cookedAt;

    @Column(name = "leftover_notes")
    private String leftoverNotes;

    @Column(name = "leftover_type")
    private LeftoverType leftoverType;

    @Column(name = "status")
    private BatchStatus status;

    @Column(name = "expiry_is_estimated")
    private boolean expiryEstimated;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public UUID getCatalogItemId() {
        return catalogItemId;
    }

    public void setCatalogItemId(UUID catalogItemId) {
        this.catalogItemId = catalogItemId;
    }

    public String getCustomItemName() {
        return customItemName;
    }

    public void setCustomItemName(String customItemName) {
        this.customItemName = customItemName;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public StorageLocation getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(StorageLocation storageLocation) {
        this.storageLocation = storageLocation;
    }

    public BigDecimal getInitialQuantity() {
        return initialQuantity;
    }

    public void setInitialQuantity(BigDecimal initialQuantity) {
        this.initialQuantity = initialQuantity;
    }

    public BigDecimal getCurrentQuantity() {
        return currentQuantity;
    }

    public void setCurrentQuantity(BigDecimal currentQuantity) {
        this.currentQuantity = currentQuantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public BigDecimal getCostInr() {
        return costInr;
    }

    public void setCostInr(BigDecimal costInr) {
        this.costInr = costInr;
    }

    public boolean isLeftover() {
        return leftover;
    }

    public void setLeftover(boolean leftover) {
        this.leftover = leftover;
    }

    public Instant getCookedAt() {
        return cookedAt;
    }

    public void setCookedAt(Instant cookedAt) {
        this.cookedAt = cookedAt;
    }

    public String getLeftoverNotes() {
        return leftoverNotes;
    }

    public void setLeftoverNotes(String leftoverNotes) {
        this.leftoverNotes = leftoverNotes;
    }

    public LeftoverType getLeftoverType() {
        return leftoverType;
    }

    public void setLeftoverType(LeftoverType leftoverType) {
        this.leftoverType = leftoverType;
    }

    public boolean isExpiryEstimated() {
        return expiryEstimated;
    }

    public void setExpiryEstimated(boolean expiryEstimated) {
        this.expiryEstimated = expiryEstimated;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public void setStatus(BatchStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
