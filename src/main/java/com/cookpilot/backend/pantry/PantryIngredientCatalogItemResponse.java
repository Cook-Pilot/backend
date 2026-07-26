package com.cookpilot.backend.pantry;

public record PantryIngredientCatalogItemResponse(
		String name,
		String emoji,
		int defaultShelfLifeDays
) {
}
