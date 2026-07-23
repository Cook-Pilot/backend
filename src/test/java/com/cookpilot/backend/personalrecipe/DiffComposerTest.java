package com.cookpilot.backend.personalrecipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cookpilot.backend.personalrecipe.DiffComposer.OriginalIngredient;
import com.cookpilot.backend.personalrecipe.DiffComposer.OriginalStep;

/**
 * 합성 로직 단위 테스트(순수 함수 — Spring/DB 불필요).
 * diff(원본 기준 누적) + 원본 → 완성된 개인 레시피가 규칙대로 나오는지 검증한다.
 */
class DiffComposerTest {

	private final UUID ice = UUID.randomUUID();
	private final UUID rum = UUID.randomUUID();
	private final UUID lime = UUID.randomUUID();

	private List<OriginalIngredient> cocktailIngredients() {
		return List.of(
				new OriginalIngredient(ice, "얼음", new BigDecimal("100.00"), "g", true, 0),
				new OriginalIngredient(rum, "럼", new BigDecimal("45.00"), "ml", true, 1),
				new OriginalIngredient(lime, "라임", new BigDecimal("0.50"), "개", false, 2));
	}

	private final UUID stepIceId = UUID.randomUUID();
	private final UUID stepPourId = UUID.randomUUID();
	private final UUID stepShakeId = UUID.randomUUID();

	private List<OriginalStep> cocktailSteps() {
		return List.of(
				new OriginalStep(stepIceId, 0, "얼음을 셰이커에 넣는다", null, null),
				new OriginalStep(stepPourId, 1, "재료를 붓는다", null, null),
				new OriginalStep(stepShakeId, 2, "10초간 흔든다", 10, null));
	}

	@Test
	void diff가_없으면_원본이_그대로_나온다() {
		List<ComposedIngredient> ingredients = DiffComposer.composeIngredients(cocktailIngredients(), List.of());
		List<ComposedStep> steps = DiffComposer.composeSteps(cocktailSteps(), List.of());

		assertThat(ingredients).extracting(ComposedIngredient::name).containsExactly("얼음", "럼", "라임");
		assertThat(ingredients).allMatch(i -> i.origin() == ComposedIngredient.Origin.ORIGINAL);
		assertThat(steps).extracting(ComposedStep::stepIndex).containsExactly(0, 1, 2);
	}

	@Test
	void MODIFY는_non_null_필드만_덮어쓴다() {
		List<ComposedIngredient> result = DiffComposer.composeIngredients(cocktailIngredients(), List.of(
				new IngredientAdjustment(rum, AdjustmentType.MODIFY, null, new BigDecimal("60.00"), null, null, 0)));

		ComposedIngredient modified = result.get(1);
		assertThat(modified.name()).isEqualTo("럼");                       // null → 원본 유지
		assertThat(modified.amount()).isEqualByComparingTo("60.00");       // 오버라이드
		assertThat(modified.unit()).isEqualTo("ml");                       // null → 원본 유지
		assertThat(modified.origin()).isEqualTo(ComposedIngredient.Origin.MODIFIED);
	}

	@Test
	void REMOVE된_재료는_결과에서_빠진다() {
		List<ComposedIngredient> result = DiffComposer.composeIngredients(cocktailIngredients(), List.of(
				new IngredientAdjustment(lime, AdjustmentType.REMOVE, null, null, null, null, 0)));

		assertThat(result).extracting(ComposedIngredient::name).containsExactly("얼음", "럼");
	}

	@Test
	void ADD_재료는_원본_참조_없이_뒤에_붙는다() {
		List<ComposedIngredient> result = DiffComposer.composeIngredients(cocktailIngredients(), List.of(
				new IngredientAdjustment(null, AdjustmentType.ADD, "소금", new BigDecimal("1.00"), "꼬집", false, 1),
				new IngredientAdjustment(null, AdjustmentType.ADD, "민트", null, null, null, 0)));

		assertThat(result).extracting(ComposedIngredient::name)
				.containsExactly("얼음", "럼", "라임", "민트", "소금"); // ADD 는 sortOrder 순
		assertThat(result.get(3).originalIngredientId()).isNull();
		assertThat(result.get(3).origin()).isEqualTo(ComposedIngredient.Origin.ADDED);
		assertThat(result.get(4).required()).isFalse();
	}

	@Test
	void 칵테일_흔들기를_젓기로_바꾸는_것은_단계_MODIFY_한_건이다() {
		List<ComposedStep> result = DiffComposer.composeSteps(cocktailSteps(), List.of(
				new StepAdjustment(stepShakeId, AdjustmentType.MODIFY, null, 0,
						"바 스푼으로 저어서 섞는다", 20, null)));

		ComposedStep stirred = result.get(2);
		assertThat(stirred.instruction()).isEqualTo("바 스푼으로 저어서 섞는다");
		assertThat(stirred.timerSeconds()).isEqualTo(20);
		assertThat(stirred.origin()).isEqualTo(ComposedStep.Origin.MODIFIED);
		assertThat(stirred.originalStepId()).isEqualTo(stepShakeId); // 원본 추적 유지
	}

	@Test
	void 단계_ADD는_앵커_뒤에_끼어들고_인덱스가_재부여된다() {
		// "2~3 사이에 소금 넣기" = 원본 1번(재료 붓기) 뒤에 끼워넣기
		List<ComposedStep> result = DiffComposer.composeSteps(cocktailSteps(), List.of(
				new StepAdjustment(null, AdjustmentType.ADD, 1, 0, "소금 한 꼬집을 넣는다", null, null)));

		assertThat(result).extracting(ComposedStep::instruction).containsExactly(
				"얼음을 셰이커에 넣는다", "재료를 붓는다", "소금 한 꼬집을 넣는다", "10초간 흔든다");
		assertThat(result).extracting(ComposedStep::stepIndex).containsExactly(0, 1, 2, 3);
		assertThat(result.get(2).origin()).isEqualTo(ComposedStep.Origin.ADDED);
	}

	@Test
	void 앵커가_마이너스1이면_맨_앞에_끼어든다() {
		List<ComposedStep> result = DiffComposer.composeSteps(cocktailSteps(), List.of(
				new StepAdjustment(null, AdjustmentType.ADD, -1, 0, "잔을 냉동실에서 꺼낸다", null, null)));

		assertThat(result.get(0).instruction()).isEqualTo("잔을 냉동실에서 꺼낸다");
		assertThat(result).hasSize(4);
	}

	@Test
	void 같은_앵커의_여러_ADD는_sortOrder_순서를_지킨다() {
		List<ComposedStep> result = DiffComposer.composeSteps(cocktailSteps(), List.of(
				new StepAdjustment(null, AdjustmentType.ADD, 1, 2, "두 번째 추가", null, null),
				new StepAdjustment(null, AdjustmentType.ADD, 1, 1, "첫 번째 추가", null, null)));

		assertThat(result).extracting(ComposedStep::instruction).containsExactly(
				"얼음을 셰이커에 넣는다", "재료를 붓는다", "첫 번째 추가", "두 번째 추가", "10초간 흔든다");
	}

	@Test
	void REMOVE된_단계_뒤에_앵커된_ADD는_살아남는다() {
		List<ComposedStep> result = DiffComposer.composeSteps(cocktailSteps(), List.of(
				new StepAdjustment(stepPourId, AdjustmentType.REMOVE, null, 0, null, null, null),
				new StepAdjustment(null, AdjustmentType.ADD, 1, 0, "재료를 천천히 층으로 쌓는다", null, null)));

		assertThat(result).extracting(ComposedStep::instruction).containsExactly(
				"얼음을 셰이커에 넣는다", "재료를 천천히 층으로 쌓는다", "10초간 흔든다");
	}

	@Test
	void 원본_범위를_벗어난_앵커의_ADD는_맨_뒤에_붙는다() {
		List<ComposedStep> result = DiffComposer.composeSteps(cocktailSteps(), List.of(
				new StepAdjustment(null, AdjustmentType.ADD, 99, 0, "가니시를 올린다", null, null)));

		assertThat(result.get(result.size() - 1).instruction()).isEqualTo("가니시를 올린다");
	}

	@Test
	void 재료와_단계를_함께_바꾸는_소금_시나리오() {
		// 사용자의 원래 질문 시나리오: 재료에 소금 추가 + "소금 넣기" 단계를 2~3 사이에 삽입
		List<ComposedIngredient> ingredients = DiffComposer.composeIngredients(cocktailIngredients(), List.of(
				new IngredientAdjustment(null, AdjustmentType.ADD, "소금", new BigDecimal("1.00"), "꼬집", false, 0)));
		List<ComposedStep> steps = DiffComposer.composeSteps(cocktailSteps(), List.of(
				new StepAdjustment(null, AdjustmentType.ADD, 1, 0, "소금 한 꼬집을 넣는다", null, null)));

		assertThat(ingredients).extracting(ComposedIngredient::name).contains("소금");
		assertThat(steps).extracting(ComposedStep::instruction)
				.containsExactly("얼음을 셰이커에 넣는다", "재료를 붓는다", "소금 한 꼬집을 넣는다", "10초간 흔든다");
	}
}
