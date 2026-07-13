package com.cookpilot.backend.personalrecipe;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PersonalRecipeVersion(
		UUID id,
		UUID userId,
		UUID recipeId,
		int versionNumber,
		String title,
		String summary,
		Map<String, Object> adjustmentPayload,
		UUID sourceSessionId,
		Instant createdAt
) {
}
