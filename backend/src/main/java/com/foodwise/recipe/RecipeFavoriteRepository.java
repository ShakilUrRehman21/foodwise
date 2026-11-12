package com.foodwise.recipe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeFavoriteRepository extends JpaRepository<RecipeFavorite, UUID> {

    Optional<RecipeFavorite> findByUserIdAndRecipeId(UUID userId, UUID recipeId);

    List<RecipeFavorite> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
