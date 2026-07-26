package com.cookpilot.backend.recommendation;

import java.time.Instant;
import java.util.UUID;

public record RecommendationEvidence(
		UUID reviewId,
		UUID recipeId,
		String recipeTitle,
		Instant cookedAt,
		int rating
) {
}
