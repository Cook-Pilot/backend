package com.cookpilot.backend.pantry;

import java.time.Instant;
import java.util.List;

public record PantryRecipeSuggestionsResponse(
		Instant generatedAt,
		List<PantryRecipeSuggestion> suggestions
) {
	public PantryRecipeSuggestionsResponse {
		suggestions = List.copyOf(suggestions);
	}
}
