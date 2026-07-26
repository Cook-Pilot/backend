package com.cookpilot.backend.recommendation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record NextCookRecommendation(
		UUID recommendationId,
		String type,
		UUID originalIngredientId,
		String ingredientName,
		BigDecimal originalAmount,
		BigDecimal suggestedAmount,
		String unit,
		int changePercent,
		BigDecimal confidence,
		String reason,
		String explanationSource,
		String model,
		String promptVersion,
		List<RecommendationEvidence> evidence
) {
	public NextCookRecommendation {
		evidence = List.copyOf(evidence);
	}
}
