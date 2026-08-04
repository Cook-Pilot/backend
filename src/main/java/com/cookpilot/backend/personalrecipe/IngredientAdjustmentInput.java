package com.cookpilot.backend.personalrecipe;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

/**
 * 재료 조정 요청 전용 DTO.
 *
 * Jackson 은 nullable Java 값만으로 JSON 키 생략과 명시적 null 을 구분할 수 없다.
 * JsonNode record component 는 Jackson 3 에서 키 생략 시 Java null, 명시적 null 시
 * NullNode 로 들어오므로 amount 의 세 상태(유지/제거/값 변경)를 손실 없이 도메인으로 옮긴다.
 */
public record IngredientAdjustmentInput(
		UUID originalIngredientId,
		AdjustmentType type,
		String name,
		@Schema(implementation = BigDecimal.class, types = {"number", "null"},
				description = "MODIFY에서 키 생략은 원본 양 유지, null은 양 제거, 숫자는 양 덮어쓰기")
		JsonNode amount,
		String unit,
		Boolean required,
		int sortOrder
) {

	IngredientAdjustment toAdjustment() {
		boolean amountSpecified = amount != null;
		BigDecimal amountValue = null;
		if (amountSpecified && !amount.isNull()) {
			if (!amount.isNumber()) {
				throw new IllegalArgumentException("재료 조정의 amount는 숫자 또는 null이어야 합니다.");
			}
			amountValue = amount.decimalValue();
		}
		return new IngredientAdjustment(
				originalIngredientId, type, name, amountValue, amountSpecified,
				unit, required, sortOrder);
	}
}
