package com.foodwise.shopping;

import com.foodwise.common.enums.AddedReason;
import com.foodwise.common.enums.Unit;
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
import org.hibernate.annotations.UpdateTimestamp;

/** JPA entity for table {@code shopping_list_items} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "shopping_list_items")
public class ShoppingListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shopping_list_id")
    private UUID shoppingListId;

    @Column(name = "catalog_item_id")
    private UUID catalogItemId;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "unit")
    private Unit unit;

    @Column(name = "estimated_cost_inr")
    private BigDecimal estimatedCostInr;

    @Column(name = "is_checked")
    private boolean checked;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(name = "checked_by_user_id")
    private UUID checkedByUserId;

    @Column(name = "added_reason")
    private AddedReason addedReason;

    @Column(name = "linked_recipe_id")
    private UUID linkedRecipeId;

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

    public UUID getShoppingListId() {
        return shoppingListId;
    }

    public void setShoppingListId(UUID shoppingListId) {
        this.shoppingListId = shoppingListId;
    }

    public UUID getCatalogItemId() {
        return catalogItemId;
    }

    public void setCatalogItemId(UUID catalogItemId) {
        this.catalogItemId = catalogItemId;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public BigDecimal getEstimatedCostInr() {
        return estimatedCostInr;
    }

    public void setEstimatedCostInr(BigDecimal estimatedCostInr) {
        this.estimatedCostInr = estimatedCostInr;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }

    public UUID getCheckedByUserId() {
        return checkedByUserId;
    }

    public void setCheckedByUserId(UUID checkedByUserId) {
        this.checkedByUserId = checkedByUserId;
    }

    public AddedReason getAddedReason() {
        return addedReason;
    }

    public void setAddedReason(AddedReason addedReason) {
        this.addedReason = addedReason;
    }

    public UUID getLinkedRecipeId() {
        return linkedRecipeId;
    }

    public void setLinkedRecipeId(UUID linkedRecipeId) {
        this.linkedRecipeId = linkedRecipeId;
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
