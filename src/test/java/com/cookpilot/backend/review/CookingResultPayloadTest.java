package com.cookpilot.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.cookpilot.backend.review.CookingResultPayload.IngredientSnapshot;
import com.cookpilot.backend.review.CookingResultPayload.StepSnapshot;

class CookingResultPayloadTest {

	private static final UUID RECIPE_ID =
			UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID INGREDIENT_ID =
			UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID STEP_ID =
			UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final Instant COOKED_AT =
			Instant.parse("2026-07-29T01:02:03.123456789Z");

	@Test
	void 프론트_완료_사실을_정규화하고_불변_순서로_보존한다() {
		List<IngredientSnapshot> ingredients = new ArrayList<>(List.of(
				ingredient(null, " 참기름 ", "1.2300", " 작은술 ", false, true, 1),
				ingredient(INGREDIENT_ID, " 밥 ", "2.000", " 공기 ", true, false, 0)));
		List<StepSnapshot> steps = new ArrayList<>(List.of(
				step(null, " 마무리한다 ", null, " ", 1),
				step(STEP_ID, " 볶는다 ", 60, " 화상 주의 ", 0)));

		CookingResultPayload payload = new CookingResultPayload(
				RECIPE_ID,
				COOKED_AT,
				null,
				new BigDecimal("2.00"),
				ingredients,
				steps);
		ingredients.clear();
		steps.clear();

		assertThat(payload.cookedAt())
				.isEqualTo(Instant.parse("2026-07-29T01:02:03.123456Z"));
		assertThat(payload.targetServings()).isEqualTo(new BigDecimal("2"));
		assertThat(payload.ingredients())
				.extracting(IngredientSnapshot::sortOrder)
				.containsExactly(0, 1);
		assertThat(payload.ingredients().getFirst())
				.extracting(
						IngredientSnapshot::name,
						IngredientSnapshot::amount,
						IngredientSnapshot::unit)
				.containsExactly("밥", new BigDecimal("2"), "공기");
		assertThat(payload.steps())
				.extracting(StepSnapshot::sortOrder)
				.containsExactly(0, 1);
		assertThat(payload.steps().getFirst().cautionNote())
				.isEqualTo("화상 주의");
		assertThat(payload.steps().getLast().cautionNote()).isNull();
		assertThatThrownBy(() -> payload.ingredients().clear())
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> payload.steps().clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void 같은_원본_ID는_중복할_수_없지만_ID가_없는_추가_항목은_여러개_허용한다() {
		assertThatCode(() -> payload(
				List.of(
						ingredient(null, "파", "1", "대", true, false, 0),
						ingredient(null, "마늘", "1", "쪽", true, false, 1)),
				List.of(
						step(null, "파를 넣는다", null, null, 0),
						step(null, "마늘을 넣는다", null, null, 1))))
				.doesNotThrowAnyException();

		assertThatThrownBy(() -> payload(
				List.of(
						ingredient(INGREDIENT_ID, "밥", "1", "공기", true, false, 0),
						ingredient(INGREDIENT_ID, "밥", "2", "공기", true, false, 1)),
				List.of(step(STEP_ID, "볶는다", null, null, 0))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("originalIngredientId");
		assertThatThrownBy(() -> payload(
				List.of(ingredient(INGREDIENT_ID, "밥", "1", "공기", true, false, 0)),
				List.of(
						step(STEP_ID, "볶는다", null, null, 0),
						step(STEP_ID, "마무리한다", null, null, 1))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("originalStepId");
	}

	@Test
	void sortOrder는_항목마다_유일한_0이상_값이어야_한다() {
		assertThatThrownBy(() -> payload(
				List.of(
						ingredient(INGREDIENT_ID, "밥", "1", "공기", true, false, 0),
						ingredient(null, "김", "1", "장", false, false, 0)),
				List.of(step(STEP_ID, "볶는다", null, null, 0))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ingredients.sortOrder");
		assertThatThrownBy(() -> payload(
				List.of(ingredient(INGREDIENT_ID, "밥", "1", "공기", true, false, 0)),
				List.of(
						step(STEP_ID, "볶는다", null, null, 0),
						step(null, "마무리한다", null, null, 0))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("steps.sortOrder");
		assertThatThrownBy(() ->
				ingredient(INGREDIENT_ID, "밥", "1", "공기", true, false, -1))
				.hasMessageContaining("0 이상");
		assertThatThrownBy(() ->
				step(STEP_ID, "볶는다", null, null, -1))
				.hasMessageContaining("0 이상");
	}

	@Test
	void targetServings와_재료_수량은_유한한_정규형만_허용한다() {
		assertThat(payload(
				new BigDecimal("99.990"),
				List.of(ingredient(
						INGREDIENT_ID,
						"밥",
						"99999999.999999999999999999",
						"g",
						true,
						false,
						0))).targetServings())
				.isEqualTo(new BigDecimal("99.99"));
		assertThat(payload(
				List.of(ingredient(
						INGREDIENT_ID,
						"밥",
						"66.66666666666666",
						"g",
						true,
						false,
						0)),
				List.of(step(STEP_ID, "볶는다", null, null, 0)))
				.ingredients().getFirst().amount())
				.isEqualTo(new BigDecimal("66.66666666666666"));

		assertThatThrownBy(() -> payload(new BigDecimal("0"), baseIngredients()))
				.hasMessageContaining("0보다");
		assertThatThrownBy(() -> payload(new BigDecimal("100"), baseIngredients()))
				.hasMessageContaining("99.99");
		assertThatThrownBy(() -> payload(new BigDecimal("0.001"), baseIngredients()))
				.hasMessageContaining("소수 둘째");
		assertThatThrownBy(() -> payload(
				List.of(ingredient(
						INGREDIENT_ID, "밥", "-0.01", "g", true, false, 0)),
				baseSteps()))
				.hasMessageContaining("0 이상");
		assertThatThrownBy(() -> payload(
				List.of(ingredient(
						INGREDIENT_ID,
						"밥",
						"100000000",
						"g",
						true,
						false,
						0)),
				baseSteps()))
				.hasMessageContaining("정수 8자리");
		assertThatThrownBy(() -> payload(
				List.of(ingredient(
						INGREDIENT_ID,
						"밥",
						"0.0000000000000000001",
						"g",
						true,
						false,
						0)),
				baseSteps()))
				.hasMessageContaining("소수 18자리");
		assertThatThrownBy(() -> payload(
				new BigDecimal("1E+2147483647"), baseIngredients()))
				.hasMessageContaining("99.99");
		assertThatThrownBy(() -> payload(
				new BigDecimal("1E-2147483647"), baseIngredients()))
				.hasMessageContaining("소수 둘째");
		assertThatThrownBy(() -> payload(
				List.of(ingredient(
						INGREDIENT_ID,
						"밥",
						"1E+2147483647",
						"g",
						true,
						false,
						0)),
				baseSteps()))
				.hasMessageContaining("정수 8자리");
		assertThatThrownBy(() -> payload(
				List.of(ingredient(
						INGREDIENT_ID,
						"밥",
						"1E-2147483647",
						"g",
						true,
						false,
						0)),
				baseSteps()))
				.hasMessageContaining("소수 18자리");
	}

	@Test
	void 필수_텍스트와_목록_크기_및_타이머_경계를_검증한다() {
		assertThatCode(() -> payload(List.of(), baseSteps()))
				.doesNotThrowAnyException();
		assertThatThrownBy(() ->
				ingredient(INGREDIENT_ID, " ", "1", "g", true, false, 0))
				.hasMessageContaining("ingredients.name");
		assertThatThrownBy(() ->
				ingredient(INGREDIENT_ID, "\u2003", "1", "g", true, false, 0))
				.hasMessageContaining("ingredients.name");
		assertThatThrownBy(() ->
				ingredient(INGREDIENT_ID, "\u00a0", "1", "g", true, false, 0))
				.hasMessageContaining("ingredients.name");
		assertThatThrownBy(() ->
				step(STEP_ID, "\n", null, null, 0))
				.hasMessageContaining("steps.instruction");
		assertThatThrownBy(() ->
				step(STEP_ID, "볶는다", -1, null, 0))
				.hasMessageContaining("timerSeconds");
		assertThatThrownBy(() -> payload(baseIngredients(), List.of()))
				.hasMessageContaining("steps 실행 스냅샷");
		assertThatThrownBy(() -> payload(null, baseSteps()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ingredients");
		assertThatThrownBy(() -> payload(
				Collections.singletonList((IngredientSnapshot) null), baseSteps()))
				.hasMessageContaining("ingredients 항목");
		assertThatThrownBy(() -> payload(
				baseIngredients(), Collections.singletonList((StepSnapshot) null)))
				.hasMessageContaining("steps 항목");
		assertThatThrownBy(() -> ingredient(
				INGREDIENT_ID,
				"가".repeat(CookingResultPayload.MAX_INGREDIENT_NAME_LENGTH + 1),
				"1",
				"g",
				true,
				false,
				0)).hasMessageContaining("ingredients.name");
		assertThatThrownBy(() -> ingredient(
				INGREDIENT_ID,
				"밥",
				"1",
				"g".repeat(CookingResultPayload.MAX_INGREDIENT_UNIT_LENGTH + 1),
				true,
				false,
				0)).hasMessageContaining("ingredients.unit");
		assertThatThrownBy(() -> step(
				STEP_ID,
				"가".repeat(CookingResultPayload.MAX_STEP_INSTRUCTION_LENGTH + 1),
				null,
				null,
				0)).hasMessageContaining("steps.instruction");
		assertThatThrownBy(() -> step(
				STEP_ID,
				"볶는다",
				null,
				"가".repeat(CookingResultPayload.MAX_STEP_CAUTION_LENGTH + 1),
				0)).hasMessageContaining("steps.cautionNote");
		assertThatThrownBy(() -> payload(
				Collections.nCopies(
						CookingResultPayload.MAX_INGREDIENT_COUNT + 1,
						baseIngredients().getFirst()),
				baseSteps()))
				.hasMessageContaining("ingredients는 최대 100개");
		assertThatThrownBy(() -> payload(
				baseIngredients(),
				Collections.nCopies(
						CookingResultPayload.MAX_STEP_COUNT + 1,
						baseSteps().getFirst())))
				.hasMessageContaining("steps는 최대 100개");
	}

	@Test
	void jsonb에_저장할_수_없는_텍스트를_모든_문자열_필드에서_거부한다() {
		assertThatThrownBy(() ->
				ingredient(INGREDIENT_ID, "밥\u0000", "1", "공기", true, false, 0))
				.hasMessageContaining("ingredients.name")
				.hasMessageContaining("NUL");
		assertThatThrownBy(() ->
				ingredient(INGREDIENT_ID, "밥", "1", "공\u0000기", true, false, 0))
				.hasMessageContaining("ingredients.unit")
				.hasMessageContaining("NUL");
		assertThatThrownBy(() ->
				step(STEP_ID, "볶\u0000는다", 60, null, 0))
				.hasMessageContaining("steps.instruction")
				.hasMessageContaining("NUL");
		assertThatThrownBy(() ->
				step(STEP_ID, "볶는다", 60, "주\u0000의", 0))
				.hasMessageContaining("steps.cautionNote")
				.hasMessageContaining("NUL");

		assertThatThrownBy(() ->
				ingredient(INGREDIENT_ID, "밥\uD800", "1", "공기", true, false, 0))
				.hasMessageContaining("ingredients.name")
				.hasMessageContaining("유니코드");
		assertThatThrownBy(() ->
				ingredient(INGREDIENT_ID, "밥\uDC00", "1", "공기", true, false, 0))
				.hasMessageContaining("ingredients.name")
				.hasMessageContaining("유니코드");
		assertThatCode(() ->
				ingredient(INGREDIENT_ID, "🍚 밥", "1", "공기", true, false, 0))
					.doesNotThrowAnyException();
	}

	@Test
	void 프론트_trim과_같이_NEL과_BOM을_공백으로_정규화한다() {
		assertThat(ingredient(
				INGREDIENT_ID,
				"\u0085밥\uFEFF",
				"1",
				"\uFEFF공기\u0085",
				true,
				false,
				0))
				.extracting(IngredientSnapshot::name, IngredientSnapshot::unit)
				.containsExactly("밥", "공기");
		assertThatThrownBy(() ->
				ingredient(
						INGREDIENT_ID,
						"\u0085\uFEFF",
						"1",
						"공기",
						true,
						false,
						0))
				.hasMessageContaining("ingredients.name");
		assertThatThrownBy(() ->
				step(STEP_ID, "\uFEFF\u0085", null, null, 0))
				.hasMessageContaining("steps.instruction");
	}

	@ParameterizedTest
	@MethodSource(
			"com.cookpilot.backend.review."
					+ "DartStringTrimTestCases#dartTrimCodePoints")
	void Dart_trim의_26개_문자를_payload의_모든_텍스트_경계에서_제거한다(
			int codePoint) {
		String boundary =
				DartStringTrimTestCases.character(codePoint);
		CookingResultPayload payload = payload(
				List.of(ingredient(
						INGREDIENT_ID,
						boundary + "밥" + boundary,
						"1",
						boundary + "공기" + boundary,
						true,
						false,
						0)),
				List.of(step(
						STEP_ID,
						boundary + "볶는다" + boundary,
						60,
						boundary + "화상 주의" + boundary,
						0)));

		assertThat(payload.ingredients().getFirst())
				.extracting(
						IngredientSnapshot::name,
						IngredientSnapshot::unit)
				.containsExactly("밥", "공기");
		assertThat(payload.steps().getFirst())
				.extracting(
						StepSnapshot::instruction,
						StepSnapshot::cautionNote)
				.containsExactly("볶는다", "화상 주의");
	}

	@ParameterizedTest
	@MethodSource(
			"com.cookpilot.backend.review."
					+ "DartStringTrimTestCases#preservedControlCodePoints")
	void U001C부터_U001F는_payload_텍스트의_양끝에서도_보존한다(
			int codePoint) {
		String boundary =
				DartStringTrimTestCases.character(codePoint);
		CookingResultPayload payload = payload(
				List.of(ingredient(
						INGREDIENT_ID,
						boundary + "밥" + boundary,
						"1",
						boundary + "공기" + boundary,
						true,
						false,
						0)),
				List.of(step(
						STEP_ID,
						boundary + "볶는다" + boundary,
						60,
						boundary + "화상 주의" + boundary,
						0)));

		assertThat(payload.ingredients().getFirst())
				.extracting(
						IngredientSnapshot::name,
						IngredientSnapshot::unit)
				.containsExactly(
						boundary + "밥" + boundary,
						boundary + "공기" + boundary);
		assertThat(payload.steps().getFirst())
				.extracting(
						StepSnapshot::instruction,
						StepSnapshot::cautionNote)
				.containsExactly(
						boundary + "볶는다" + boundary,
						boundary + "화상 주의" + boundary);
	}

	@Test
	void cookedAt은_PostgreSQL과_같은_microsecond_및_4자리_연도_경계를_쓴다() {
		assertThat(payloadAt(Instant.parse("0001-01-01T00:00:00Z")).cookedAt())
				.isEqualTo(Instant.parse("0001-01-01T00:00:00Z"));
		assertThat(payloadAt(
				Instant.parse("9999-12-31T23:59:59.999999999Z")).cookedAt())
				.isEqualTo(Instant.parse("9999-12-31T23:59:59.999999Z"));
		assertThatThrownBy(() ->
				payloadAt(Instant.parse("0000-12-31T23:59:59.999999Z")))
				.hasMessageContaining("0001-01-01");
		assertThatThrownBy(() ->
				payloadAt(Instant.parse("+10000-01-01T00:00:00Z")))
				.hasMessageContaining("9999-12-31");
	}

	private CookingResultPayload payload(
			List<IngredientSnapshot> ingredients, List<StepSnapshot> steps) {
		return payload(new BigDecimal("2"), ingredients, steps);
	}

	private CookingResultPayload payload(
			BigDecimal targetServings, List<IngredientSnapshot> ingredients) {
		return payload(targetServings, ingredients, baseSteps());
	}

	private CookingResultPayload payload(
			BigDecimal targetServings,
			List<IngredientSnapshot> ingredients,
			List<StepSnapshot> steps) {
		return new CookingResultPayload(
				RECIPE_ID,
				COOKED_AT,
				null,
				targetServings,
				ingredients,
				steps);
	}

	private CookingResultPayload payloadAt(Instant cookedAt) {
		return new CookingResultPayload(
				RECIPE_ID,
				cookedAt,
				null,
				new BigDecimal("2"),
				baseIngredients(),
				baseSteps());
	}

	private List<IngredientSnapshot> baseIngredients() {
		return List.of(ingredient(
				INGREDIENT_ID, "밥", "1", "공기", true, false, 0));
	}

	private List<StepSnapshot> baseSteps() {
		return List.of(step(STEP_ID, "볶는다", 60, null, 0));
	}

	private IngredientSnapshot ingredient(
			UUID id,
			String name,
			String amount,
			String unit,
			boolean required,
			boolean omitted,
			int sortOrder) {
		return new IngredientSnapshot(
				id,
				name,
				amount == null ? null : new BigDecimal(amount),
				unit,
				required,
				omitted,
				sortOrder);
	}

	private StepSnapshot step(
			UUID id,
			String instruction,
			Integer timerSeconds,
			String cautionNote,
			int sortOrder) {
		return new StepSnapshot(
				id, instruction, timerSeconds, cautionNote, sortOrder);
	}
}
