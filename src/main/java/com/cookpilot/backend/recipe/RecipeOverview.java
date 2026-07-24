package com.cookpilot.backend.recipe;

import java.util.UUID;

public record RecipeOverview(
		UUID id,
		String title,
		String description,
		String imageUrl
) {
}
