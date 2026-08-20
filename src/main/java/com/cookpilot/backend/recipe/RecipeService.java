package com.cookpilot.backend.recipe;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;

@Service
@Transactional(readOnly = true)
public class RecipeService {

	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final RecipeStepRepository recipeStepRepository;

	public RecipeService(RecipeRepository recipeRepository,
			RecipeIngredientRepository recipeIngredientRepository,
			RecipeStepRepository recipeStepRepository) {
		this.recipeRepository = recipeRepository;
		this.recipeIngredientRepository = recipeIngredientRepository;
		this.recipeStepRepository = recipeStepRepository;
	}

	public List<RecipeOverview> findAll() {
		return recipeRepository.findByStatusOrderByTitleAscIdAsc("active").stream()
				.map(this::toOverview)
				.toList();
	}

	public Page<RecipeOverview> search(String title, String ingredient, int page, int size) {
		String normalizedTitle = escapeLikePattern(title == null ? "" : title.trim());
		String normalizedIngredient = escapeLikePattern(ingredient == null ? "" : ingredient.trim());
		int normalizedPage = Math.max(page, 1);
		int normalizedSize = Math.min(Math.max(size, 1), 50);
		Page<RecipeEntity> result = recipeRepository.search(
				"active",
				normalizedTitle,
				normalizedIngredient,
				PageRequest.of(normalizedPage - 1, normalizedSize));
		int lastPage = Math.max(result.getTotalPages(), 1);
		if (normalizedPage > lastPage) {
			result = recipeRepository.search(
					"active",
					normalizedTitle,
					normalizedIngredient,
					PageRequest.of(lastPage - 1, normalizedSize));
		}
		return result.map(this::toOverview);
	}

	private String escapeLikePattern(String value) {
		return value
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
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
				steps,
				entity.getSourceType(),
				entity.getSourceRef());
	}
}
