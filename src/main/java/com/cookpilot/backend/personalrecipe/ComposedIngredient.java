package com.cookpilot.backend.personalrecipe;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 원본 + diff 를 합성한 "완성된 내 재료" 1행. 프론트는 이 목록을 그대로 렌더링하면 된다.
 * origin 으로 각 행이 원본 그대로인지/수정됐는지/새로 추가됐는지 드러난다(= 재료 트래킹).
 */
public record ComposedIngredient(
		UUID originalIngredientId,
		String name,
		BigDecimal amount,
		String unit,
		boolean required,
		Origin origin
) {

	public enum Origin {
		ORIGINAL, MODIFIED, ADDED
	}
}
