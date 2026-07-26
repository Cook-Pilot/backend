package com.cookpilot.backend.pantry;

import java.time.LocalDate;

/**
 * useByDate가 없으면 카탈로그의 기본 소진 기한으로 오늘부터 계산해 채운다.
 */
public record AddPantryItemRequest(
		String ingredientName,
		LocalDate useByDate
) {
}
