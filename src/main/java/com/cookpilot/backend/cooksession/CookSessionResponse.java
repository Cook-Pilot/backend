package com.cookpilot.backend.cooksession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cookpilot.backend.recipe.RecipeStep;

public record CookSessionResponse(
		UUID id,
		UUID userId,
		UUID recipeId,
		UUID personalVersionId,
		String recipeTitle,
		SessionStatus status,
		int currentStepIndex,
		RecipeStep currentStep,
		int totalSteps,
		List<RecipeStep> steps,
		Instant startedAt,
		Instant completedAt,
		Instant abortedAt
) {

	public static CookSessionResponse from(CookSession session) {
		return new CookSessionResponse(
				session.getId(),
				session.getUserId(),
				session.getRecipeId(),
				session.getPersonalVersionId(),
				session.getRecipeTitle(),
				session.getStatus(),
				session.getCurrentStepIndex(),
				session.currentStep(),
				session.getStepSnapshot().size(),
				session.getStepSnapshot(),
				session.getStartedAt(),
				session.getCompletedAt(),
				session.getAbortedAt()
		);
	}
}
