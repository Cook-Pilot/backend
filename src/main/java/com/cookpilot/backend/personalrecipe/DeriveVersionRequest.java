package com.cookpilot.backend.personalrecipe;

import java.util.List;

/**
 * 버전 파생 요청. diff 는 원본 레시피 기준 누적 전체 집합이다.
 *
 * adjustments 가 null 이면 "부모 diff 를 그대로 복사"(제목/요약만 바꾼 파생).
 * null 이 아니면 주어진 집합이 새 버전의 diff 전체가 된다
 * (프론트가 부모 diff 를 받아 수정한 결과를 통째로 보내는 계약).
 */
public record DeriveVersionRequest(
		String title,
		String summary,
		List<IngredientAdjustment> ingredientAdjustments,
		List<StepAdjustment> stepAdjustments
) {
}
