package com.cookpilot.backend.recipe;

import java.util.UUID;

public record RecipeStep(
		UUID id,
		int stepIndex,
		String instruction,
		Integer timerSeconds,
		String cautionNote,
		String imageUrl
) {
}
