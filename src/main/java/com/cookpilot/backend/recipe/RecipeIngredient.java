package com.cookpilot.backend.recipe;

import java.util.UUID;

public record RecipeIngredient(
		UUID id,
		String name,
		Double amount,
		String unit,
		boolean required
) {
}
