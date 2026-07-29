package com.cookpilot.backend.review;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 프론트가 한 번의 조리를 끝낸 시점에 보유한 실행 결과 스냅샷.
 *
 * <p>이 타입은 최종 재료·단계라는 사실만 보존한다. 원본과 비교해
 * ADD/MODIFY/REMOVE를 추론하지 않으며, 이슈 #34의 {@code setup/cooking/review}
 * 수정 파이프라인 입력도 아니다. {@code clientSessionId}는 payload가 아니라
 * 완료 리소스의 별도 멱등성 키로 관리한다.</p>
 */
public record CookingResultPayload(
		UUID recipeId,
		Instant cookedAt,
		UUID sourcePersonalVersionId,
		BigDecimal targetServings,
		List<IngredientSnapshot> ingredients,
		List<StepSnapshot> steps
) {

	public static final int MAX_INGREDIENT_COUNT = 100;
	public static final int MAX_STEP_COUNT = 100;
	public static final int MAX_INGREDIENT_NAME_LENGTH = 200;
	public static final int MAX_INGREDIENT_UNIT_LENGTH = 50;
	public static final int MAX_STEP_INSTRUCTION_LENGTH = 4_000;
	public static final int MAX_STEP_CAUTION_LENGTH = 1_000;

	private static final BigDecimal MAX_TARGET_SERVINGS = new BigDecimal("99.99");
	private static final BigDecimal MAX_INGREDIENT_AMOUNT =
			new BigDecimal("99999999.999999999999999999");
	private static final int MAX_INGREDIENT_AMOUNT_SCALE = 18;
	private static final Instant MIN_COOKED_AT =
			Instant.parse("0001-01-01T00:00:00Z");
	private static final Instant MAX_COOKED_AT =
			Instant.parse("9999-12-31T23:59:59.999999Z");

	public CookingResultPayload {
		recipeId = required(recipeId, "recipeId");
		cookedAt = normalizeCookedAt(cookedAt);
		targetServings = normalizeTargetServings(targetServings);
		ingredients = normalizeIngredients(ingredients);
		steps = normalizeSteps(steps);
	}

	static Instant normalizeCookedAt(Instant value) {
		Instant normalized = required(value, "cookedAt")
				.truncatedTo(ChronoUnit.MICROS);
		if (normalized.isBefore(MIN_COOKED_AT)
				|| normalized.isAfter(MAX_COOKED_AT)) {
			throw new IllegalArgumentException(
					"cookedAt은 0001-01-01부터 9999-12-31 사이여야 합니다.");
		}
		return normalized;
	}

	private static BigDecimal normalizeTargetServings(BigDecimal value) {
		BigDecimal present = required(value, "targetServings");
		if (present.signum() <= 0) {
			throw new IllegalArgumentException("targetServings는 0보다 커야 합니다.");
		}
		if (present.compareTo(MAX_TARGET_SERVINGS) > 0) {
			throw targetServingsOutOfRange();
		}
		BigDecimal normalized = normalizeIntegerScale(present.stripTrailingZeros());
		if (normalized.scale() > 2) {
			throw targetServingsOutOfRange();
		}
		return normalized;
	}

	private static IllegalArgumentException targetServingsOutOfRange() {
		return new IllegalArgumentException(
				"targetServings는 최대 99.99, 소수 둘째 자리까지 입력할 수 있습니다.");
	}

	private static List<IngredientSnapshot> normalizeIngredients(
			List<IngredientSnapshot> values) {
		// 현재 프론트는 단계가 하나라도 있어야 조리를 시작하지만 재료 없는
		// 레시피는 허용한다. 따라서 ingredients의 빈 목록도 완전한 사실이다.
		List<IngredientSnapshot> normalized = immutableItems(
				values, MAX_INGREDIENT_COUNT, "ingredients").stream()
				.sorted(Comparator.comparingInt(IngredientSnapshot::sortOrder))
				.toList();
		assertUniqueOrder(normalized.stream()
				.map(IngredientSnapshot::sortOrder).toList(), "ingredients");
		assertUniqueIds(normalized.stream()
				.map(IngredientSnapshot::originalIngredientId).toList(),
				"ingredients.originalIngredientId");
		return normalized;
	}

	private static List<StepSnapshot> normalizeSteps(List<StepSnapshot> values) {
		List<StepSnapshot> normalized = immutableItems(
				values, MAX_STEP_COUNT, "steps").stream()
				.sorted(Comparator.comparingInt(StepSnapshot::sortOrder))
				.toList();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("steps 실행 스냅샷은 필수입니다.");
		}
		assertUniqueOrder(normalized.stream()
				.map(StepSnapshot::sortOrder).toList(), "steps");
		assertUniqueIds(normalized.stream()
				.map(StepSnapshot::originalStepId).toList(),
				"steps.originalStepId");
		return normalized;
	}

	private static <T> List<T> immutableItems(
			List<T> values, int maximum, String field) {
		List<T> present = required(values, field);
		if (present.size() > maximum) {
			throw new IllegalArgumentException(
					field + "는 최대 " + maximum + "개까지 입력할 수 있습니다.");
		}
		if (present.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException(
					field + " 항목은 null일 수 없습니다.");
		}
		return List.copyOf(present);
	}

	private static void assertUniqueOrder(List<Integer> orders, String field) {
		if (new HashSet<>(orders).size() != orders.size()) {
			throw new IllegalArgumentException(
					field + ".sortOrder는 중복될 수 없습니다.");
		}
	}

	private static void assertUniqueIds(List<UUID> ids, String field) {
		Set<UUID> seen = new HashSet<>();
		for (UUID id : ids) {
			if (id != null && !seen.add(id)) {
				throw new IllegalArgumentException(
						field + "는 중복될 수 없습니다.");
			}
		}
	}

	private static String requiredText(
			String value, int maximum, String field) {
		String normalized = nullableText(value, maximum, field);
		if (normalized == null) {
			throw new IllegalArgumentException(field + "는 필수입니다.");
		}
		return normalized;
	}

	private static String nullableText(
			String value, int maximum, String field) {
		if (value == null) {
			return null;
		}
		assertJsonbSafeText(value, field);
		String normalized = DartStringTrim.trim(value);
		if (normalized.isEmpty()) {
			return null;
		}
		if (normalized.length() > maximum) {
			throw new IllegalArgumentException(
					field + "는 최대 " + maximum + "자까지 입력할 수 있습니다.");
		}
		return normalized;
	}

	private static void assertJsonbSafeText(String value, String field) {
		for (int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			if (current == '\u0000') {
				throw new IllegalArgumentException(
						field + "에는 NUL 문자를 입력할 수 없습니다.");
			}
			if (Character.isHighSurrogate(current)) {
				if (index + 1 >= value.length()
						|| !Character.isLowSurrogate(value.charAt(index + 1))) {
					throw new IllegalArgumentException(
							field + "에는 올바르지 않은 유니코드를 입력할 수 없습니다.");
				}
				index++;
			}
			else if (Character.isLowSurrogate(current)) {
				throw new IllegalArgumentException(
						field + "에는 올바르지 않은 유니코드를 입력할 수 없습니다.");
			}
		}
	}

	private static BigDecimal normalizeIngredientAmount(BigDecimal value) {
		if (value == null) {
			return null;
		}
		if (value.signum() < 0) {
			throw new IllegalArgumentException(
					"ingredients.amount는 0 이상이어야 합니다.");
		}
		if (value.compareTo(MAX_INGREDIENT_AMOUNT) > 0) {
			throw ingredientAmountOutOfRange();
		}
		BigDecimal normalized = normalizeIntegerScale(value.stripTrailingZeros());
		if (normalized.scale() > MAX_INGREDIENT_AMOUNT_SCALE) {
			throw ingredientAmountOutOfRange();
		}
		return normalized;
	}

	private static IllegalArgumentException ingredientAmountOutOfRange() {
		return new IllegalArgumentException(
				"ingredients.amount는 정수 8자리, 소수 18자리 범위여야 합니다.");
	}

	private static BigDecimal normalizeIntegerScale(BigDecimal value) {
		return value.scale() < 0 ? value.setScale(0) : value;
	}

	private static <T> T required(T value, String field) {
		if (value == null) {
			throw new IllegalArgumentException(field + "는 필수입니다.");
		}
		return value;
	}

	public record IngredientSnapshot(
			UUID originalIngredientId,
			String name,
			BigDecimal amount,
			String unit,
			boolean required,
			boolean omitted,
			int sortOrder
	) {

		public IngredientSnapshot {
			name = requiredText(
					name, MAX_INGREDIENT_NAME_LENGTH, "ingredients.name");
			amount = normalizeIngredientAmount(amount);
			unit = nullableText(
					unit, MAX_INGREDIENT_UNIT_LENGTH, "ingredients.unit");
			if (sortOrder < 0) {
				throw new IllegalArgumentException(
						"ingredients.sortOrder는 0 이상이어야 합니다.");
			}
		}
	}

	public record StepSnapshot(
			UUID originalStepId,
			String instruction,
			Integer timerSeconds,
			String cautionNote,
			int sortOrder
	) {

		public StepSnapshot {
			// v1 프론트 완료 스냅샷에는 단계 생략 필드가 없다. 없는 사실을
			// 기본값으로 만들어 저장하지 않고 현재 공개 가능한 값만 보존한다.
			instruction = requiredText(
					instruction,
					MAX_STEP_INSTRUCTION_LENGTH,
					"steps.instruction");
			cautionNote = nullableText(
					cautionNote, MAX_STEP_CAUTION_LENGTH, "steps.cautionNote");
			if (timerSeconds != null && timerSeconds < 0) {
				throw new IllegalArgumentException(
						"steps.timerSeconds는 0 이상이어야 합니다.");
			}
			if (sortOrder < 0) {
				throw new IllegalArgumentException(
						"steps.sortOrder는 0 이상이어야 합니다.");
			}
		}
	}
}
