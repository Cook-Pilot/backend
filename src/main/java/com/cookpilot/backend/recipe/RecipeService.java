package com.cookpilot.backend.recipe;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RecipeService {

	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final RecipeStepRepository recipeStepRepository;

	public List<RecipeOverview> findAll() {
		return recipeRepository.findByStatusOrderByTitleAscIdAsc("active").stream()
				.map(this::toOverview)
				.toList();
	}

	public Recipe findById(UUID recipeId) {
		RecipeEntity entity = recipeRepository.findById(recipeId)
				.orElseThrow(() -> new NotFoundException("레시피를 찾을 수 없습니다: " + recipeId));
		return toRecipe(entity);
	}

	private RecipeOverview toOverview(RecipeEntity entity) {
		return new RecipeOverview(
				entity.getId(),
				entity.getTitle(),
				entity.getDescription(),
				entity.getImageUrl());
	}

	private Recipe toRecipe(RecipeEntity entity) {
		List<RecipeIngredient> ingredients = recipeIngredientRepository
				.findByRecipeIdOrderBySortOrderAsc(entity.getId())
				.stream()
				.map(ingredient -> new RecipeIngredient(
						ingredient.getId(),
						ingredient.getName(),
						ingredient.getAmount() == null ? null : ingredient.getAmount().doubleValue(),
						ingredient.getUnit(),
						ingredient.isRequired()))
				.toList();

		List<RecipeStep> steps = recipeStepRepository
				.findByRecipeIdOrderByStepIndexAsc(entity.getId())
				.stream()
				.map(step -> new RecipeStep(
						step.getId(),
						step.getStepIndex(),
						step.getInstruction(),
						step.getTimerSeconds(),
						step.getCautionNote(),
						step.getImageUrl()))
				.toList();

		return new Recipe(
				entity.getId(),
				entity.getTitle(),
				entity.getDescription(),
				entity.getBaseServings().doubleValue(),
				entity.getImageUrl(),
				ingredients,
				steps);
	}
}
