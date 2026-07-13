package com.cookpilot.backend.recipe;

public record RecipeIngredient(
		String name,
		Double amount,
		String unit,
		boolean required
) {
}
