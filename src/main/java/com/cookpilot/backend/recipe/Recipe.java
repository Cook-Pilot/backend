package com.cookpilot.backend.recipe;

import java.util.List;
import java.util.UUID;

public record Recipe(
		UUID id,
		String title,
		String description,
		Double baseServings,
		String imageUrl,
		List<RecipeIngredient> ingredients,
		List<RecipeStep> steps,
		String sourceType,
		String sourceRef
) {
}
