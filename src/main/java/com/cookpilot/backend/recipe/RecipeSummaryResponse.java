package com.cookpilot.backend.recipe;

import java.util.UUID;

public record RecipeSummaryResponse(
		UUID id,
		String title,
		String description,
		boolean hasPersonalVersion,
		UUID latestPersonalVersionId
) {
}
