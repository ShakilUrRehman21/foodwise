package com.foodwise.mealplan;

import com.foodwise.common.enums.MealPlanStatus;
import com.foodwise.common.enums.MealSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** JPA entity for table {@code meal_plans} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "meal_plans")
public class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kitchen_id")
    private UUID kitchenId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "plan_date")
    private LocalDate planDate;

    @Column(name = "meal_slot")
    private MealSlot mealSlot;

    @Column(name = "recipe_id")
    private UUID recipeId;

    @Column(name = "custom_meal_title")
    private String customMealTitle;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status")
    private MealPlanStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public MealSlot getMealSlot() {
        return mealSlot;
    }

    public void setMealSlot(MealSlot mealSlot) {
        this.mealSlot = mealSlot;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(UUID recipeId) {
        this.recipeId = recipeId;
    }

    public String getCustomMealTitle() {
        return customMealTitle;
    }

    public void setCustomMealTitle(String customMealTitle) {
        this.customMealTitle = customMealTitle;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public MealPlanStatus getStatus() {
        return status;
    }

    public void setStatus(MealPlanStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
