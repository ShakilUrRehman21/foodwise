package com.foodwise.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FoodCatalogRepository extends JpaRepository<FoodCatalogItem, UUID> {
}
