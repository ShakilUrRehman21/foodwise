package com.foodwise.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FoodCategoryRepository extends JpaRepository<FoodCategory, UUID> {

    List<FoodCategory> findAllByOrderByDisplayOrderAsc();

    Optional<FoodCategory> findBySlug(String slug);
}
