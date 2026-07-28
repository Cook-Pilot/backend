package com.cookpilot.backend.recommendation.explanation;

import java.math.BigDecimal;
import java.util.List;

import com.cookpilot.backend.recommendation.RecommendationEvidence;

public record RecommendationExplanationContext(
		String targetRecipeTitle,
		String ingredientName,
		BigDecimal originalAmount,
		BigDecimal suggestedAmount,
		String unit,
		int changePercent,
		List<RecommendationEvidence> evidence
) {
	public RecommendationExplanationContext {
		evidence = List.copyOf(evidence);
	}
}
