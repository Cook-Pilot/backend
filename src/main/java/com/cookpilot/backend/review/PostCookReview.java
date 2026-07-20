package com.cookpilot.backend.review;

import java.time.Instant;
import java.util.UUID;

public record PostCookReview(
		UUID id,
		UUID cookSessionId,
		UUID userId,
		UUID recipeId,
		int rating,
		String comment,
		String nextTimeNote,
		UUID createdPersonalVersionId,
		Instant createdAt
) {
}
