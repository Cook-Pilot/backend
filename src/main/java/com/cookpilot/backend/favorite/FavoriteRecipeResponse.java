package com.cookpilot.backend.favorite;

import java.time.Instant;
import java.util.UUID;

public record FavoriteRecipeResponse(
		UUID id,
		String title,
		String description,
		String imageUrl,
		Instant favoritedAt,
		boolean hasPersonalVersion,
		UUID latestPersonalVersionId
) {
}
