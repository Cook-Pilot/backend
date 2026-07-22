package com.cookpilot.backend.recipe;

public record RecipeStep(
		int stepIndex,
		String instruction,
		Integer timerSeconds,
		String cautionNote,
		String imageUrl
) {
}
