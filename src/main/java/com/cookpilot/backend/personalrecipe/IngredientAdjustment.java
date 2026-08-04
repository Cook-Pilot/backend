package com.cookpilot.backend.personalrecipe;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 재료 diff 1건 (API 노출 + 파생 요청 공용).
 *
 * ADD    — originalIngredientId 없이 name/amount/unit/required 를 통째로 가진다.
 * MODIFY — originalIngredientId 필수. 필드 생략은 원본 값 유지, 지정한 필드는 오버라이드.
 *          amount 만 nullable 오버라이드를 지원한다: 키 생략은 유지, 명시적 null 은 양 제거.
 * REMOVE — originalIngredientId 필수. 나머지 필드는 무시된다.
 */
public record IngredientAdjustment(
		UUID originalIngredientId,
		AdjustmentType type,
		String name,
		@Schema(types = {"number", "null"},
				description = "MODIFY에서 값은 양 덮어쓰기, null은 양 제거. 키 생략은 원본 양 유지")
		BigDecimal amount,
		@Schema(accessMode = Schema.AccessMode.READ_ONLY,
				description = "MODIFY에서 amount 키가 지정됐는지. null 제거와 키 생략을 구분")
		boolean amountSpecified,
		String unit,
		Boolean required,
		int sortOrder
) {
	public IngredientAdjustment {
		// non-null 값은 기존 생성자/기존 DB 행에서도 항상 오버라이드다. presence 는
		// MODIFY amount 에만 의미가 있으므로 ADD/REMOVE 에서는 정규화해 버린다.
		amountSpecified = type == AdjustmentType.MODIFY && (amountSpecified || amount != null);
	}

	/** 기존 내부 호출 호환: non-null MODIFY amount 는 지정된 값으로 간주한다. */
	public IngredientAdjustment(UUID originalIngredientId, AdjustmentType type, String name,
			BigDecimal amount, String unit, Boolean required, int sortOrder) {
		this(originalIngredientId, type, name, amount, amount != null,
				unit, required, sortOrder);
	}

	static IngredientAdjustment from(PersonalIngredientAdjustmentEntity entity) {
		return new IngredientAdjustment(
				entity.getOriginalIngredientId(),
				entity.getAdjustmentType(),
				entity.getName(),
				entity.getAmount(),
				entity.isAmountOverridePresent()
						|| (entity.getAdjustmentType() == AdjustmentType.MODIFY
								&& entity.getAmount() != null),
				entity.getUnit(),
				entity.getRequired(),
				entity.getSortOrder()
		);
	}
}
