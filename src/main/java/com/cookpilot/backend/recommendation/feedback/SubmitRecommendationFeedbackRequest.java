package com.cookpilot.backend.recommendation.feedback;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SubmitRecommendationFeedbackRequest(
		UUID originalIngredientId,
		RecommendationDecision decision,
		BigDecimal originalAmount,
		BigDecimal suggestedAmount,
		BigDecimal appliedAmount,
		String unit,
		String reason,
		String explanationSource,
		String model,
		String promptVersion,
		List<UUID> evidenceReviewIds
) {
	public SubmitRecommendationFeedbackRequest {
		evidenceReviewIds = evidenceReviewIds == null
				? List.of()
				: List.copyOf(evidenceReviewIds);
	}
}
