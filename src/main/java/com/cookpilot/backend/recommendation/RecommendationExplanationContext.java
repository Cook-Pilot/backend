package com.cookpilot.backend.recommendation;

import java.math.BigDecimal;
import java.util.List;

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
