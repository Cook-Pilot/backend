package com.cookpilot.backend.recipe;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredientEntity, UUID> {

	List<RecipeIngredientEntity> findByRecipeIdOrderBySortOrderAsc(UUID recipeId);
}
