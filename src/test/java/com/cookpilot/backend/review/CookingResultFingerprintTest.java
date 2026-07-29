package com.cookpilot.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.cookpilot.backend.review.CookingResultPayload.IngredientSnapshot;
import com.cookpilot.backend.review.CookingResultPayload.StepSnapshot;

class CookingResultFingerprintTest {

	private static final UUID RECIPE_ID =
			UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID SOURCE_VERSION_ID =
			UUID.fromString("40000000-0000-0000-0000-000000000001");
	private static final UUID INGREDIENT_ID =
			UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID STEP_ID =
			UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final Instant COOKED_AT =
			Instant.parse("2026-07-29T01:02:03.123456789Z");

	@Test
	void v1_projection의_golden_hash를_유지한다() {
		assertThat(hash(basePayload()))
				.isEqualTo(
						"2f309fef36edcebcb3f690e5dfe1ab8e038344e3b6c6d10d36e1ed39e6fca5a1");
	}

	@Test
	void 정규화된_사실이_같으면_decimal_scale과_입력_배열_순서를_무시한다() {
		CookingResultPayload equivalent = new CookingResultPayload(
				RECIPE_ID,
				Instant.parse("2026-07-29T01:02:03.123456001Z"),
				SOURCE_VERSION_ID,
				new BigDecimal("2.000"),
				List.of(
						ingredient(null, "참기름", "1.000", "작은술", false, true, 1),
						ingredient(INGREDIENT_ID, "밥", "2.00", "공기", true, false, 0)),
				List.of(
						step(null, "마무리한다", null, null, 1),
						step(STEP_ID, "볶는다", 60, "화상 주의", 0)));

		assertThat(equivalent).isEqualTo(basePayload());
		assertThat(hash(equivalent)).isEqualTo(hash(basePayload()));
	}

	@Test
	void 최상위_완료_사실이_바뀌면_fingerprint가_바뀐다() {
		CookingResultPayload base = basePayload();
		String original = hash(base);

		assertDifferent(original, payload(
				UUID.fromString("10000000-0000-0000-0000-000000000002"),
				base.cookedAt(),
				base.sourcePersonalVersionId(),
				base.targetServings(),
				base.ingredients(),
				base.steps()));
		assertDifferent(original, payload(
				base.recipeId(),
				base.cookedAt().plusNanos(1_000),
				base.sourcePersonalVersionId(),
				base.targetServings(),
				base.ingredients(),
				base.steps()));
		assertDifferent(original, payload(
				base.recipeId(),
				base.cookedAt(),
				null,
				base.targetServings(),
				base.ingredients(),
				base.steps()));
		assertDifferent(original, payload(
				base.recipeId(),
				base.cookedAt(),
				base.sourcePersonalVersionId(),
				new BigDecimal("3"),
				base.ingredients(),
				base.steps()));
	}

	@Test
	void 재료의_모든_필드와_목록_길이를_fingerprint에_포함한다() {
		CookingResultPayload base = basePayload();
		IngredientSnapshot item = base.ingredients().getFirst();
		String original = hash(base);
		List<IngredientSnapshot> variants = List.of(
				ingredient(
						UUID.fromString("20000000-0000-0000-0000-000000000002"),
						item.name(), "2", item.unit(), true, false, 0),
				ingredient(INGREDIENT_ID, "현미밥", "2", item.unit(), true, false, 0),
				ingredient(INGREDIENT_ID, item.name(), "2.1", item.unit(), true, false, 0),
				ingredient(INGREDIENT_ID, item.name(), null, item.unit(), true, false, 0),
				ingredient(INGREDIENT_ID, item.name(), "2", "그릇", true, false, 0),
				ingredient(INGREDIENT_ID, item.name(), "2", null, true, false, 0),
				ingredient(INGREDIENT_ID, item.name(), "2", item.unit(), false, false, 0),
				ingredient(INGREDIENT_ID, item.name(), "2", item.unit(), true, true, 0));

		for (IngredientSnapshot variant : variants) {
			assertDifferent(original, withIngredients(
					base, List.of(variant, base.ingredients().getLast())));
		}
		assertDifferent(original, withIngredients(
				base, List.of(base.ingredients().getFirst())));

		CookingResultPayload singleIngredient = withIngredients(
				base,
				List.of(ingredient(
						INGREDIENT_ID,
						item.name(),
						"2",
						item.unit(),
						true,
						false,
						0)));
		assertDifferent(
				hash(singleIngredient),
				withIngredients(
						base,
						List.of(ingredient(
								INGREDIENT_ID,
								item.name(),
								"2",
								item.unit(),
								true,
								false,
								1))));
	}

	@Test
	void 단계의_모든_필드와_목록_길이를_fingerprint에_포함한다() {
		CookingResultPayload base = basePayload();
		StepSnapshot item = base.steps().getFirst();
		String original = hash(base);
		List<StepSnapshot> variants = List.of(
				step(
						UUID.fromString("30000000-0000-0000-0000-000000000002"),
						item.instruction(), 60, item.cautionNote(), 0),
				step(STEP_ID, "약불로 볶는다", 60, item.cautionNote(), 0),
				step(STEP_ID, item.instruction(), 61, item.cautionNote(), 0),
				step(STEP_ID, item.instruction(), null, item.cautionNote(), 0),
				step(STEP_ID, item.instruction(), 60, "기름 주의", 0),
				step(STEP_ID, item.instruction(), 60, null, 0));

		for (StepSnapshot variant : variants) {
			assertDifferent(original, withSteps(
					base, List.of(variant, base.steps().getLast())));
		}
		assertDifferent(original, withSteps(
				base, List.of(base.steps().getFirst())));

		CookingResultPayload singleStep = withSteps(
				base,
				List.of(step(
						STEP_ID,
						item.instruction(),
						60,
						item.cautionNote(),
						0)));
		assertDifferent(
				hash(singleStep),
				withSteps(
						base,
						List.of(step(
								STEP_ID,
								item.instruction(),
								60,
								item.cautionNote(),
								1))));
	}

	@Test
	void 미지원_version과_잘못된_hex를_허용하지_않는다() {
		CookingResultPayload base = basePayload();
		String fingerprint = hash(base);

		assertThatThrownBy(() ->
				CookingResultFingerprint.sha256((short) 2, base))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("지원하지 않는");
		assertThat(CookingResultFingerprint.matches(fingerprint, fingerprint))
				.isTrue();
		assertThat(CookingResultFingerprint.matches(
				fingerprint, "0".repeat(64))).isFalse();
		assertThat(CookingResultFingerprint.matches(
				"é".repeat(64), "ø".repeat(64))).isFalse();
		assertThat(CookingResultFingerprint.matches(
				fingerprint.toUpperCase(), fingerprint)).isFalse();
		assertThat(CookingResultFingerprint.matches(
				fingerprint.substring(1), fingerprint)).isFalse();
		assertThat(CookingResultFingerprint.matches(null, fingerprint)).isFalse();
	}

	@ParameterizedTest
	@MethodSource(
			"com.cookpilot.backend.review."
					+ "DartStringTrimTestCases#dartTrimCodePoints")
	void Dart_trim_문자는_경계에서_fingerprint를_바꾸지_않고_내부에서는_바꾼다(
			int codePoint) {
		CookingResultPayload base = basePayload();
		IngredientSnapshot item = base.ingredients().getFirst();
		String character =
				DartStringTrimTestCases.character(codePoint);
		CookingResultPayload boundaryVariant = withIngredients(
				base,
				List.of(
						ingredient(
								item.originalIngredientId(),
								character + item.name()
										+ character,
								item.amount().toPlainString(),
								item.unit(),
								item.required(),
								item.omitted(),
								item.sortOrder()),
						base.ingredients().getLast()));
		CookingResultPayload internalVariant = withIngredients(
				base,
				List.of(
						ingredient(
								item.originalIngredientId(),
								item.name() + character + "추가",
								item.amount().toPlainString(),
								item.unit(),
								item.required(),
								item.omitted(),
								item.sortOrder()),
						base.ingredients().getLast()));

		assertThat(boundaryVariant).isEqualTo(base);
		assertThat(hash(boundaryVariant)).isEqualTo(hash(base));
		assertThat(hash(internalVariant)).isNotEqualTo(hash(base));
	}

	@ParameterizedTest
	@MethodSource(
			"com.cookpilot.backend.review."
					+ "DartStringTrimTestCases#preservedControlCodePoints")
	void U001C부터_U001F는_보존되어_canonical_fingerprint를_구분한다(
			int codePoint) {
		CookingResultPayload base = basePayload();
		IngredientSnapshot item = base.ingredients().getFirst();
		String character =
				DartStringTrimTestCases.character(codePoint);
		CookingResultPayload changed = withIngredients(
				base,
				List.of(
						ingredient(
								item.originalIngredientId(),
								character + item.name()
										+ character,
								item.amount().toPlainString(),
								item.unit(),
								item.required(),
								item.omitted(),
								item.sortOrder()),
						base.ingredients().getLast()));

		assertThat(changed.ingredients().getFirst().name())
				.isEqualTo(character + item.name() + character);
		assertThat(hash(changed)).isNotEqualTo(hash(base));
	}

	private String hash(CookingResultPayload payload) {
		return CookingResultFingerprint.sha256(payload);
	}

	private void assertDifferent(
			String original, CookingResultPayload changed) {
		assertThat(hash(changed)).isNotEqualTo(original);
	}

	private CookingResultPayload withIngredients(
			CookingResultPayload base, List<IngredientSnapshot> ingredients) {
		return payload(
				base.recipeId(),
				base.cookedAt(),
				base.sourcePersonalVersionId(),
				base.targetServings(),
				ingredients,
				base.steps());
	}

	private CookingResultPayload withSteps(
			CookingResultPayload base, List<StepSnapshot> steps) {
		return payload(
				base.recipeId(),
				base.cookedAt(),
				base.sourcePersonalVersionId(),
				base.targetServings(),
				base.ingredients(),
				steps);
	}

	private CookingResultPayload payload(
			UUID recipeId,
			Instant cookedAt,
			UUID sourceVersionId,
			BigDecimal targetServings,
			List<IngredientSnapshot> ingredients,
			List<StepSnapshot> steps) {
		return new CookingResultPayload(
				recipeId,
				cookedAt,
				sourceVersionId,
				targetServings,
				ingredients,
				steps);
	}

	private CookingResultPayload basePayload() {
		return payload(
				RECIPE_ID,
				COOKED_AT,
				SOURCE_VERSION_ID,
				new BigDecimal("2"),
				List.of(
						ingredient(INGREDIENT_ID, "밥", "2", "공기", true, false, 0),
						ingredient(null, "참기름", "1", "작은술", false, true, 1)),
				List.of(
						step(STEP_ID, "볶는다", 60, "화상 주의", 0),
						step(null, "마무리한다", null, null, 1)));
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
