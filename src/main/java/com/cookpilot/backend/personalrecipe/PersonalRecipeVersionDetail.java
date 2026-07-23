package com.cookpilot.backend.personalrecipe;

import java.util.List;

/**
 * 개인 버전 상세 응답: 메타 + 합성 결과(그대로 렌더링 가능) + 원시 diff(무엇이 바뀌었는지).
 * 프론트/AI 계약의 기준 형태.
 */
public record PersonalRecipeVersionDetail(
		PersonalRecipeVersion version,
		List<ComposedIngredient> ingredients,
		List<ComposedStep> steps,
		List<IngredientAdjustment> ingredientAdjustments,
		List<StepAdjustment> stepAdjustments
) {
}
