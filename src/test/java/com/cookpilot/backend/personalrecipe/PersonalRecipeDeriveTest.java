package com.cookpilot.backend.personalrecipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.review.PostCookReview;
import com.cookpilot.backend.review.ReviewService;

/**
 * 개인 버전 파생 흐름 통합 테스트(실제 postgres, V2 시드 라면 레시피 사용).
 *
 * 리뷰 → v1(빈 diff) → derive 로 v2/v3 을 쌓으며:
 *  - 버전 번호/계보(parent)/기본(is_default) 전환
 *  - diff 저장과 합성(원본 + diff) 결과
 *  - 파생 시 부모 diff 복사(요청 diff null)
 *  - 검증 실패(다른 레시피 재료 참조, ADD 필수값 누락)
 * 를 검증한다. @Transactional 롤백으로 시드/다른 테스트와 간섭하지 않는다.
 */
@Transactional
class PersonalRecipeDeriveTest extends PostgresApiTestBase {

	// V2__personal_diff_and_seed.sql 의 라면 시드 고정 UUID
	private static final UUID RAMEN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ING_WATER = UUID.fromString("20000000-0000-0000-0000-000000000102");
	private static final UUID ING_EGG = UUID.fromString("20000000-0000-0000-0000-000000000103");
	private static final UUID STEP_BOIL = UUID.fromString("30000000-0000-0000-0000-000000000101");

	@Autowired
	private ReviewService reviewService;
	@Autowired
	private PersonalRecipeService personalRecipeService;
	@Autowired
	private PersonalIngredientAdjustmentRepository ingredientAdjustmentRepository;

	private PersonalRecipeVersion createV1() {
		PostCookReview review = reviewService.submit(RAMEN_ID, 4, "국물이 싱거웠다", "다음엔 물을 줄이자");
		return personalRecipeService.findById(review.createdPersonalVersionId());
	}

	@Test
	void 리뷰가_v1을_만들고_리뷰를_역참조한다() {
		PostCookReview review = reviewService.submit(RAMEN_ID, 5, "좋았다", null);
		PersonalRecipeVersion v1 = personalRecipeService.findById(review.createdPersonalVersionId());

		assertThat(v1.versionNumber()).isEqualTo(1);
		assertThat(v1.sourceReviewId()).isEqualTo(review.id());
		assertThat(v1.parentVersionId()).isNull();
		assertThat(v1.isDefault()).isTrue();
	}

	@Test
	void 파생하면_버전이_쌓이고_기본_버전이_넘어간다() {
		PersonalRecipeVersion v1 = createV1();

		PersonalRecipeVersion v2 = personalRecipeService.derive(v1.id(), new DeriveVersionRequest(
				"물 줄인 라면", "물 400ml", List.of(
						new IngredientAdjustment(ING_WATER, AdjustmentType.MODIFY, null,
								new BigDecimal("400.00"), null, null, 0)),
				List.of()));

		assertThat(v2.versionNumber()).isEqualTo(v1.versionNumber() + 1);
		assertThat(v2.parentVersionId()).isEqualTo(v1.id());
		assertThat(v2.isDefault()).isTrue();
		assertThat(personalRecipeService.findById(v1.id()).isDefault()).isFalse();
		assertThat(personalRecipeService.findLatestByRecipe(RAMEN_ID).orElseThrow().id())
				.isEqualTo(v2.id());
	}

	@Test
	void 파생_버전의_상세는_원본과_diff의_합성이다() {
		PersonalRecipeVersion v1 = createV1();

		PersonalRecipeVersion v2 = personalRecipeService.derive(v1.id(), new DeriveVersionRequest(
				"내 라면", null,
				List.of(
						new IngredientAdjustment(ING_WATER, AdjustmentType.MODIFY, null,
								new BigDecimal("400.00"), null, null, 0),
						new IngredientAdjustment(ING_EGG, AdjustmentType.REMOVE, null, null, null, null, 0),
						new IngredientAdjustment(null, AdjustmentType.ADD, "치즈",
								new BigDecimal("1.00"), "장", false, 0)),
				List.of(
						new StepAdjustment(STEP_BOIL, AdjustmentType.MODIFY, null, 0,
								"물 400ml를 넣고 3분간 끓이세요.", null, null),
						new StepAdjustment(null, AdjustmentType.ADD, 1, 0,
								"불을 끄기 직전에 치즈를 올리세요.", null, null))));

		PersonalRecipeVersionDetail detail = personalRecipeService.findDetailById(v2.id());

		// 재료: 물 400 오버라이드, 계란 제외, 치즈 추가 (원본: 라면/물/계란/파)
		assertThat(detail.ingredients()).extracting(ComposedIngredient::name)
				.containsExactly("라면", "물", "파", "치즈");
		assertThat(detail.ingredients().get(1).amount()).isEqualByComparingTo("400.00");
		assertThat(detail.ingredients().get(1).origin()).isEqualTo(ComposedIngredient.Origin.MODIFIED);
		assertThat(detail.ingredients().get(3).origin()).isEqualTo(ComposedIngredient.Origin.ADDED);

		// 단계: 0번 오버라이드, 1번 뒤에 치즈 단계 삽입, 인덱스 재부여
		assertThat(detail.steps()).extracting(ComposedStep::instruction).containsExactly(
				"물 400ml를 넣고 3분간 끓이세요.",
				"건더기, 분말스프, 면을 넣고 3분간 끓이세요.",
				"불을 끄기 직전에 치즈를 올리세요.",
				"불을 끄고 그릇에 옮겨 담으세요.");
		assertThat(detail.steps()).extracting(ComposedStep::stepIndex).containsExactly(0, 1, 2, 3);

		// 원시 diff 도 그대로 노출된다(무엇이 바뀌었는지 = 트래킹)
		assertThat(detail.ingredientAdjustments()).hasSize(3);
		assertThat(detail.stepAdjustments()).hasSize(2);
	}

	@Test
	void 요청_diff가_null이면_부모_diff를_복사한다() {
		PersonalRecipeVersion v1 = createV1();
		PersonalRecipeVersion v2 = personalRecipeService.derive(v1.id(), new DeriveVersionRequest(
				null, null,
				List.of(new IngredientAdjustment(null, AdjustmentType.ADD, "소금",
						new BigDecimal("1.00"), "꼬집", false, 0)),
				List.of()));

		PersonalRecipeVersion v3 = personalRecipeService.derive(v2.id(),
				new DeriveVersionRequest("복사 파생", null, null, null));

		List<PersonalIngredientAdjustmentEntity> v3Diffs = ingredientAdjustmentRepository
				.findByPersonalVersionIdOrderBySortOrderAsc(v3.id());
		assertThat(v3Diffs).hasSize(1);
		assertThat(v3Diffs.get(0).getName()).isEqualTo("소금");
		// 복사본이지 공유가 아니다: v2 의 diff 행은 그대로 남아 있다
		assertThat(ingredientAdjustmentRepository.findByPersonalVersionIdOrderBySortOrderAsc(v2.id()))
				.hasSize(1);
	}

	@Test
	void 다른_레시피의_재료를_참조하면_거부된다() {
		PersonalRecipeVersion v1 = createV1();
		UUID friedRiceIngredient = UUID.fromString("20000000-0000-0000-0000-000000000201"); // 밥(김치볶음밥)

		assertThatThrownBy(() -> personalRecipeService.derive(v1.id(), new DeriveVersionRequest(
				null, null,
				List.of(new IngredientAdjustment(friedRiceIngredient, AdjustmentType.REMOVE,
						null, null, null, null, 0)),
				List.of())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이 레시피의 재료가 아닙니다");
	}

	@Test
	void ADD_재료에_이름이_없으면_거부된다() {
		PersonalRecipeVersion v1 = createV1();

		assertThatThrownBy(() -> personalRecipeService.derive(v1.id(), new DeriveVersionRequest(
				null, null,
				List.of(new IngredientAdjustment(null, AdjustmentType.ADD, " ",
						null, null, null, 0)),
				List.of())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("name은 필수");
	}

	@Test
	void ADD_단계에_앵커가_없으면_거부된다() {
		PersonalRecipeVersion v1 = createV1();

		assertThatThrownBy(() -> personalRecipeService.derive(v1.id(), new DeriveVersionRequest(
				null, null, List.of(),
				List.of(new StepAdjustment(null, AdjustmentType.ADD, null, 0, "새 단계", null, null)))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("insertAfterStepIndex");
	}
}
