package com.cookpilot.backend.pantry;

import java.time.LocalDate;
import java.util.UUID;

public record PantryItemResponse(
		UUID id,
		String ingredientName,
		String emoji,
		LocalDate useByDate,
		int daysUntilExpiry
) {
}
