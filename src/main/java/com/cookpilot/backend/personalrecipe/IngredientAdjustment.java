package com.cookpilot.backend.personalrecipe;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 재료 diff 1건 (API 노출 + 파생 요청 공용).
 *
 * ADD    — originalIngredientId 없이 name/amount/unit/required 를 통째로 가진다.
 * MODIFY — originalIngredientId 필수. null 필드는 원본 값 유지, non-null 은 오버라이드.
 * REMOVE — originalIngredientId 필수. 나머지 필드는 무시된다.
 */
public record IngredientAdjustment(
		UUID originalIngredientId,
		AdjustmentType type,
		String name,
		BigDecimal amount,
		String unit,
		Boolean required,
		int sortOrder
) {

	static IngredientAdjustment from(PersonalIngredientAdjustmentEntity entity) {
		return new IngredientAdjustment(
				entity.getOriginalIngredientId(),
				entity.getAdjustmentType(),
				entity.getName(),
				entity.getAmount(),
				entity.getUnit(),
				entity.getRequired(),
				entity.getSortOrder()
		);
	}
}
