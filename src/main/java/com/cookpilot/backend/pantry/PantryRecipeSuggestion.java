package com.cookpilot.backend.pantry;

import java.util.List;
import java.util.UUID;

public record PantryRecipeSuggestion(
		UUID recipeId,
		String recipeTitle,
		String recipeImageUrl,
		List<PantryMatchedIngredient> matchedIngredients,
		int mostUrgentDaysUntilExpiry
) {
	public PantryRecipeSuggestion {
		matchedIngredients = List.copyOf(matchedIngredients);
	}
}
