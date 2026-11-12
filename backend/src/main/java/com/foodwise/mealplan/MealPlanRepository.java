package com.foodwise.mealplan;

import com.foodwise.common.enums.MealSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {

    Optional<MealPlan> findByKitchenIdAndPlanDateAndMealSlot(UUID kitchenId, LocalDate planDate, MealSlot mealSlot);

    List<MealPlan> findByKitchenIdAndPlanDateBetweenOrderByPlanDateAscMealSlotAsc(UUID kitchenId, LocalDate from, LocalDate to);

    Optional<MealPlan> findByIdAndKitchenId(UUID id, UUID kitchenId);
}
