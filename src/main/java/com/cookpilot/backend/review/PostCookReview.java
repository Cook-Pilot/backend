package com.cookpilot.backend.review;

import java.time.Instant;
import java.util.UUID;

public record PostCookReview(
		UUID id,
		UUID userId,
		UUID recipeId,
		Integer rating,
		String comment,
		String nextTimeNote,
		UUID createdPersonalVersionId,
		Instant createdAt
) {
}
