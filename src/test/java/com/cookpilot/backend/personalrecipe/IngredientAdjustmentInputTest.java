package com.cookpilot.backend.personalrecipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** Jackson 3 요청 경계에서 amount 키 생략과 명시적 null 이 보존되는지 검증한다. */
class IngredientAdjustmentInputTest {

	private final JsonMapper objectMapper = JsonMapper.builder().build();

	@Test
	void amount_키_생략은_원본_유지로_변환된다() throws Exception {
		IngredientAdjustment adjustment = read("""
				{"type":"MODIFY","sortOrder":0}
				""").toAdjustment();

		assertThat(adjustment.amount()).isNull();
		assertThat(adjustment.amountSpecified()).isFalse();
	}

	@Test
	void amount_명시적_null은_양_제거로_변환된다() throws Exception {
		IngredientAdjustment adjustment = read("""
				{"type":"MODIFY","amount":null,"sortOrder":0}
				""").toAdjustment();

		assertThat(adjustment.amount()).isNull();
		assertThat(adjustment.amountSpecified()).isTrue();
	}

	@Test
	void amount_숫자는_값_오버라이드로_변환된다() throws Exception {
		IngredientAdjustment adjustment = read("""
				{"type":"MODIFY","amount":12.50,"sortOrder":0}
				""").toAdjustment();

		assertThat(adjustment.amount()).isEqualByComparingTo(new BigDecimal("12.50"));
		assertThat(adjustment.amountSpecified()).isTrue();
	}

	@Test
	void amount_문자열은_400으로_옮길_검증_예외가_된다() throws Exception {
		IngredientAdjustmentInput input = read("""
				{"type":"MODIFY","amount":"12.50","sortOrder":0}
				""");

		assertThatThrownBy(input::toAdjustment)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("숫자 또는 null");
	}

	private IngredientAdjustmentInput read(String json) throws Exception {
		return objectMapper.readValue(json, IngredientAdjustmentInput.class);
	}
}
