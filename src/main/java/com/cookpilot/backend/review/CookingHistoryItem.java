package com.cookpilot.backend.review;

import java.time.Instant;
import java.util.UUID;

public record CookingHistoryItem(
		UUID reviewId,
		UUID recipeId,
		String recipeTitle,
		String recipeImageUrl,
		Instant cookedAt,
		Integer rating,
		String comment,
		String nextTimeNote,
		UUID sourcePersonalVersionId,
		UUID createdPersonalVersionId,
		Integer createdPersonalVersionNumber,
		String createdPersonalVersionSummary
) {
}
