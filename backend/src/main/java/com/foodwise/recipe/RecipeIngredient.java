package com.foodwise.recipe;

import com.foodwise.common.enums.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** JPA entity for table {@code recipe_ingredients} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipe_id")
    private UUID recipeId;

    @Column(name = "catalog_item_id")
    private UUID catalogItemId;

    @Column(name = "ingredient_name")
    private String ingredientName;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "unit")
    private Unit unit;

    @Column(name = "is_optional")
    private boolean optional;

    @Column(name = "is_pantry_staple")
    private boolean pantryStaple;

    @Column(name = "substitute_notes")
    private String substituteNotes;

    @Column(name = "display_order")
    private int displayOrder;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(UUID recipeId) {
        this.recipeId = recipeId;
    }

    public UUID getCatalogItemId() {
        return catalogItemId;
    }

    public void setCatalogItemId(UUID catalogItemId) {
        this.catalogItemId = catalogItemId;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public void setIngredientName(String ingredientName) {
        this.ingredientName = ingredientName;
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

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public boolean isPantryStaple() {
        return pantryStaple;
    }

    public void setPantryStaple(boolean pantryStaple) {
        this.pantryStaple = pantryStaple;
    }

    public String getSubstituteNotes() {
        return substituteNotes;
    }

    public void setSubstituteNotes(String substituteNotes) {
        this.substituteNotes = substituteNotes;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
