package com.cookpilot.backend.review;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cookpilot.backend.personalrecipe.ExecutedRecipe;

public record SubmitReviewRequest(
		UUID clientSessionId,
		UUID recipeId,
		Instant cookedAt,
		BigDecimal targetServings,
		UUID sourcePersonalVersionId,
		Integer rating,
		String comment,
		String nextTimeNote,
		List<ExecutedIngredientRequest> ingredients,
		List<ExecutedStepRequest> steps
) {

	public ExecutedRecipe toExecutedRecipe() {
		List<ExecutedRecipe.ExecutedIngredient> executedIngredients = ingredients == null
				? List.of()
				: ingredients.stream()
						.map(item -> {
							if (item == null) {
								throw new IllegalArgumentException("ingredients 항목은 null일 수 없습니다.");
							}
							return new ExecutedRecipe.ExecutedIngredient(
									item.originalIngredientId(),
									item.name(),
									item.amount(),
									item.unit(),
									item.required(),
									item.omitted(),
									item.sortOrder());
						})
						.toList();
		List<ExecutedRecipe.ExecutedStep> executedSteps = steps == null
				? List.of()
				: steps.stream()
						.map(item -> {
							if (item == null) {
								throw new IllegalArgumentException("steps 항목은 null일 수 없습니다.");
							}
							return new ExecutedRecipe.ExecutedStep(
									item.originalStepId(),
									item.instruction(),
									item.timerSeconds(),
									item.cautionNote(),
									item.sortOrder());
						})
						.toList();
		return new ExecutedRecipe(
				sourcePersonalVersionId,
				targetServings,
				executedIngredients,
				executedSteps);
	}

	public record ExecutedIngredientRequest(
			UUID originalIngredientId,
			String name,
			BigDecimal amount,
			String unit,
			Boolean required,
			boolean omitted,
			int sortOrder
	) {
	}

	public record ExecutedStepRequest(
			UUID originalStepId,
			String instruction,
			Integer timerSeconds,
			String cautionNote,
			int sortOrder
	) {
	}
}
