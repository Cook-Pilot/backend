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
									Boolean.TRUE.equals(item.omitted()),
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
									Boolean.TRUE.equals(item.omitted()),
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
			// Jackson 3 는 primitive 생성자 인자가 JSON 에 없으면 역직렬화를 거부한다.
			// 필드 생략을 false(실행함)로 허용하기 위해 박싱 타입으로 받는다.
			Boolean omitted,
			int sortOrder
	) {
	}

	public record ExecutedStepRequest(
			UUID originalStepId,
			String instruction,
			Integer timerSeconds,
			String cautionNote,
			// ExecutedIngredientRequest.omitted 와 같은 이유로 박싱 타입.
			Boolean omitted,
			int sortOrder
	) {
	}
}
