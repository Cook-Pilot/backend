package com.cookpilot.backend.recommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecommendationFeedbackResponse(
		UUID id,
		UUID recommendationId,
		UUID recipeId,
		UUID originalIngredientId,
		RecommendationDecision decision,
		BigDecimal appliedAmount,
		Instant createdAt
) {
	static RecommendationFeedbackResponse from(RecommendationFeedbackEntity entity) {
		return new RecommendationFeedbackResponse(
				entity.getId(),
				entity.getRecommendationId(),
				entity.getRecipeId(),
				entity.getOriginalIngredientId(),
				entity.getDecision(),
				entity.getAppliedAmount(),
				entity.getCreatedAt());
	}
}
