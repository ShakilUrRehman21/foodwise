package com.foodwise.recipe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    Optional<Recipe> findBySlug(String slug);
}
