package com.cookpilot.backend.recipe;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeStepRepository extends JpaRepository<RecipeStepEntity, UUID> {

	List<RecipeStepEntity> findByRecipeIdOrderByStepIndexAsc(UUID recipeId);
}
