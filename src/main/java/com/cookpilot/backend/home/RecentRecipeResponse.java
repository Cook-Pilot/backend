package com.cookpilot.backend.home;

import java.time.Instant;
import java.util.UUID;

public record RecentRecipeResponse(
		UUID id,
		String title,
		String description,
		String imageUrl,
		Instant lastCookedAt,
		Integer lastRating,
		boolean hasPersonalVersion,
		UUID latestPersonalVersionId,
		boolean favorite
) {
}
