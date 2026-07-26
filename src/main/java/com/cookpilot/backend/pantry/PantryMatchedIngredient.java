package com.cookpilot.backend.pantry;

public record PantryMatchedIngredient(
		String ingredientName,
		String emoji,
		int daysUntilExpiry
) {
}
