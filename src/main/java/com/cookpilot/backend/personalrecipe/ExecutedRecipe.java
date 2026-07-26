package com.cookpilot.backend.personalrecipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 한 번의 조리에서 실제로 실행한 레시피 상태.
 *
 * 원본 재료·단계 ID가 있으면 원본의 어느 항목에서 출발했는지 추적할 수 있고,
 * ID가 없으면 사용자가 새로 추가한 항목으로 해석한다.
 */
public record ExecutedRecipe(
		UUID sourcePersonalVersionId,
		BigDecimal targetServings,
		List<ExecutedIngredient> ingredients,
		List<ExecutedStep> steps
) {

	public record ExecutedIngredient(
			UUID originalIngredientId,
			String name,
			BigDecimal amount,
			String unit,
			Boolean required,
			boolean omitted,
			int sortOrder
	) {
	}

	public record ExecutedStep(
			UUID originalStepId,
			String instruction,
			Integer timerSeconds,
			String cautionNote,
			boolean omitted,
			int sortOrder
	) {
	}
}
