package com.cookpilot.backend.recommendation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NextCookRecommendationResponse(
		UUID recipeId,
		Instant generatedAt,
		List<NextCookRecommendation> recommendations
) {
	public NextCookRecommendationResponse {
		recommendations = List.copyOf(recommendations);
	}
}
