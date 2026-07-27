package com.cookpilot.backend.recommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 추천 판정 규칙 단위 테스트. DB·Spring 컨텍스트 없이 순수 함수만 검증한다.
 */
class RecommendationRuleEngineTest {

	private static final UUID TARGET_ID = UUID.randomUUID();
	private static final Instant BASE = Instant.parse("2026-07-20T10:00:00Z");

	@Nested
	class 맛_프로파일_유사도 {

		@Test
		void 같은_레시피면_taxonomy를_보지_않고_1이다() {
			RecommendationRuleEngine.FlavorProfile korean = profile(
					"KOREAN", "STEW", List.of("BOIL"), List.of("DOENJANG"));
			RecommendationRuleEngine.FlavorProfile western = profile(
					"WESTERN", "NOODLE", List.of("BAKE"), List.of("OLIVE_OIL"));

			assertThat(RecommendationRuleEngine.profileSimilarity(korean, western, true))
					.isEqualTo(1.0);
		}

		@Test
		void 근거_레시피에_프로파일이_없으면_0이다() {
			RecommendationRuleEngine.FlavorProfile korean = profile(
					"KOREAN", "STEW", List.of("BOIL"), List.of("DOENJANG"));

			assertThat(RecommendationRuleEngine.profileSimilarity(korean, null, false))
					.isZero();
			assertThat(RecommendationRuleEngine.profileSimilarity(korean, null, true))
					.isZero();
		}

		@Test
		void cuisine과_sauceBase가_같으면_0_70으로_컷라인을_넘는다() {
			double similarity = RecommendationRuleEngine.profileSimilarity(
					profile("KOREAN", "STEW", List.of("BOIL"), List.of("DOENJANG")),
					profile("KOREAN", "RICE", List.of("STIR_FRY"), List.of("DOENJANG")),
					false);

			assertThat(similarity).isEqualTo(0.70);
			assertThat(RecommendationRuleEngine.passesSimilarity(similarity)).isTrue();
		}

		@Test
		void sauceBase가_다르면_나머지를_다_맞춰도_0_55로_탈락한다() {
			double similarity = RecommendationRuleEngine.profileSimilarity(
					profile("KOREAN", "STEW", List.of("BOIL"), List.of("DOENJANG")),
					profile("KOREAN", "STEW", List.of("BOIL"), List.of("GOCHUJANG")),
					false);

			assertThat(similarity).isEqualTo(0.55);
			assertThat(RecommendationRuleEngine.passesSimilarity(similarity)).isFalse();
		}

		@Test
		void sauceBase만_같으면_0_45로_탈락한다() {
			double similarity = RecommendationRuleEngine.profileSimilarity(
					profile("KOREAN", "STEW", List.of("BOIL"), List.of("SOY_SAUCE")),
					profile("WESTERN", "NOODLE", List.of("BAKE"), List.of("SOY_SAUCE")),
					false);

			assertThat(similarity).isEqualTo(0.45);
			assertThat(RecommendationRuleEngine.passesSimilarity(similarity)).isFalse();
		}

		@Test
		void 대소문자가_달라도_같은_값으로_본다() {
			double similarity = RecommendationRuleEngine.profileSimilarity(
					profile("korean", "stew", List.of("boil"), List.of("doenjang")),
					profile("KOREAN", "STEW", List.of("BOIL"), List.of("DOENJANG")),
					false);

			assertThat(similarity).isEqualTo(1.0);
		}
	}

	@Nested
	class 근거_비율_필터 {

		@Test
		void 범위_안이고_변화가_충분하면_통과한다() {
			assertThat(RecommendationRuleEngine.isUsableRatio(1.5)).isTrue();
			assertThat(RecommendationRuleEngine.isUsableRatio(0.5)).isTrue();
		}

		@Test
		void 경계값_0_25와_2_0은_포함한다() {
			assertThat(RecommendationRuleEngine.isUsableRatio(0.25)).isTrue();
			assertThat(RecommendationRuleEngine.isUsableRatio(2.0)).isTrue();
			assertThat(RecommendationRuleEngine.isUsableRatio(0.24)).isFalse();
			assertThat(RecommendationRuleEngine.isUsableRatio(2.01)).isFalse();
		}

		@Test
		void 변화가_5퍼센트_미만이면_체감_못한다고_보고_버린다() {
			assertThat(RecommendationRuleEngine.isUsableRatio(1.04)).isFalse();
			assertThat(RecommendationRuleEngine.isUsableRatio(0.96)).isFalse();
			assertThat(RecommendationRuleEngine.isUsableRatio(1.05)).isTrue();
			assertThat(RecommendationRuleEngine.isUsableRatio(0.95)).isTrue();
		}
	}

	@Nested
	class 추천_초안_생성 {

		@Test
		void 근거가_1회뿐이면_추천하지_않는다() {
			RecommendationRuleEngine.Draft draft = RecommendationRuleEngine.draft(
					ingredient("10"),
					List.of(sample("1.5", 1.0, UUID.randomUUID(), BASE)));

			assertThat(draft).isNull();
		}

		@Test
		void 같은_레시피_근거_2회면_가중평균과_confidence를_낸다() {
			RecommendationRuleEngine.Draft draft = RecommendationRuleEngine.draft(
					ingredient("10"),
					List.of(
							sample("1.5", 1.0, UUID.randomUUID(), BASE),
							sample("1.5", 1.0, UUID.randomUUID(), BASE.plusSeconds(3600))));

			assertThat(draft).isNotNull();
			// 10 * 1.5
			assertThat(draft.suggestedAmount()).isEqualByComparingTo("15");
			assertThat(draft.changePercent()).isEqualTo(50);
			// 0.45 + 2*0.10 + 1.0*0.20
			assertThat(draft.confidence()).isEqualByComparingTo("0.85");
			assertThat(draft.evidence()).hasSize(2);
		}

		@Test
		void 유사도가_낮은_근거는_가중평균에서_덜_반영된다() {
			RecommendationRuleEngine.Draft draft = RecommendationRuleEngine.draft(
					ingredient("10"),
					List.of(
							sample("2.0", 1.0, UUID.randomUUID(), BASE),
							sample("1.0", 0.7, UUID.randomUUID(), BASE.plusSeconds(60))));

			// (2.0*1.0 + 1.0*0.7) / 1.7 = 1.5882…
			assertThat(draft).isNotNull();
			assertThat(draft.suggestedAmount()).isEqualByComparingTo("15.88");
			assertThat(draft.changePercent()).isEqualTo(59);
			// averageSimilarity = 1.7/2 = 0.85 → 0.45 + 0.20 + 0.17 = 0.82
			assertThat(draft.confidence()).isEqualByComparingTo("0.82");
		}

		@Test
		void 유사도가_전부_0이면_추천하지_않는다() {
			// 가중치 합이 0이면 가중평균이 정의되지 않는다(NaN). 호출자의 유사도
			// 필터와 무관하게 draft 자체가 방어해야 한다.
			RecommendationRuleEngine.Draft draft = RecommendationRuleEngine.draft(
					ingredient("10"),
					List.of(
							sample("1.5", 0.0, UUID.randomUUID(), BASE),
							sample("1.5", 0.0, UUID.randomUUID(), BASE.plusSeconds(60))));

			assertThat(draft).isNull();
		}

		@Test
		void 가중평균이_5퍼센트_안으로_수렴하면_추천하지_않는다() {
			RecommendationRuleEngine.Draft draft = RecommendationRuleEngine.draft(
					ingredient("10"),
					List.of(
							sample("1.5", 1.0, UUID.randomUUID(), BASE),
							sample("0.5", 1.0, UUID.randomUUID(), BASE.plusSeconds(60))));

			assertThat(draft).isNull();
		}

		@Test
		void 같은_리뷰가_여러_번_들어오면_최신_것만_남긴다() {
			UUID duplicated = UUID.randomUUID();
			RecommendationRuleEngine.Draft draft = RecommendationRuleEngine.draft(
					ingredient("10"),
					List.of(
							sample("0.5", 1.0, duplicated, BASE),
							sample("1.5", 1.0, duplicated, BASE.plusSeconds(3600)),
							sample("1.5", 1.0, UUID.randomUUID(), BASE.plusSeconds(7200))));

			// 중복 제거 후 1.5 두 건만 남아야 한다(0.5 가 섞이면 수렴해서 null 이 된다).
			assertThat(draft).isNotNull();
			assertThat(draft.evidence()).hasSize(2);
			assertThat(draft.suggestedAmount()).isEqualByComparingTo("15");
		}

		@Test
		void confidence는_0_95를_넘지_않는다() {
			List<RecommendationRuleEngine.EvidenceSample> samples = new ArrayList<>();
			for (int index = 0; index < 6; index++) {
				samples.add(sample(
						"1.5", 1.0, UUID.randomUUID(), BASE.plusSeconds(index * 60L)));
			}

			RecommendationRuleEngine.Draft draft =
					RecommendationRuleEngine.draft(ingredient("10"), samples);

			// 0.45 + 6*0.10 + 0.20 = 1.25 → 상한 적용
			assertThat(draft).isNotNull();
			assertThat(draft.confidence()).isEqualByComparingTo("0.95");
		}

		@Test
		void 근거는_최대_5건까지만_실어_보낸다() {
			List<RecommendationRuleEngine.EvidenceSample> samples = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				samples.add(sample(
						"1.5", 1.0, UUID.randomUUID(), BASE.plusSeconds(index * 60L)));
			}

			RecommendationRuleEngine.Draft draft =
					RecommendationRuleEngine.draft(ingredient("10"), samples);

			assertThat(draft).isNotNull();
			assertThat(draft.evidence()).hasSize(5);
		}

		@Test
		void 대상_재료_정보를_초안에_그대로_담는다() {
			RecommendationRuleEngine.Draft draft = RecommendationRuleEngine.draft(
					ingredient("10"),
					List.of(
							sample("0.5", 1.0, UUID.randomUUID(), BASE),
							sample("0.5", 1.0, UUID.randomUUID(), BASE.plusSeconds(60))));

			assertThat(draft).isNotNull();
			assertThat(draft.targetIngredient().id()).isEqualTo(TARGET_ID);
			assertThat(draft.targetIngredient().name()).isEqualTo("진간장");
			assertThat(draft.targetIngredient().unit()).isEqualTo("큰술");
			assertThat(draft.changePercent()).isEqualTo(-50);
		}
	}

	@Nested
	class 거절_이후_재제안 {

		@Test
		void 피드백이_없으면_제외하지_않는다() {
			assertThat(RecommendationRuleEngine.isRejectedWithoutNewEvidence(
					null, List.of(evidence(UUID.randomUUID(), BASE))))
					.isFalse();
		}

		@Test
		void 거절_이후_새_조리_기록이_없으면_다시_올리지_않는다() {
			boolean blocked = RecommendationRuleEngine.isRejectedWithoutNewEvidence(
					BASE.plusSeconds(3600),
					List.of(evidence(UUID.randomUUID(), BASE)));

			assertThat(blocked).isTrue();
		}

		@Test
		void 거절_이후_새_조리_기록이_생기면_다시_올린다() {
			boolean blocked = RecommendationRuleEngine.isRejectedWithoutNewEvidence(
					BASE,
					List.of(evidence(UUID.randomUUID(), BASE.plusSeconds(3600))));

			assertThat(blocked).isFalse();
		}

		@Test
		void 거절_시각과_근거_시각이_같으면_다시_올리지_않는다() {
			boolean blocked = RecommendationRuleEngine.isRejectedWithoutNewEvidence(
					BASE, List.of(evidence(UUID.randomUUID(), BASE)));

			assertThat(blocked).isTrue();
		}
	}

	@Nested
	class 재료명_정규화 {

		@Test
		void 공백을_지우고_소문자로_맞춘다() {
			assertThat(RecommendationRuleEngine.normalizeName("진 간 장"))
					.isEqualTo("진간장");
			assertThat(RecommendationRuleEngine.normalizeName("Olive Oil"))
					.isEqualTo("oliveoil");
		}

		@Test
		void null은_빈_문자열로_본다() {
			assertThat(RecommendationRuleEngine.normalizeName(null)).isEmpty();
		}
	}

	// --- fixture ---

	private static RecommendationRuleEngine.FlavorProfile profile(
			String cuisine, String dishType, List<String> methods, List<String> sauces) {
		return new RecommendationRuleEngine.FlavorProfile(
				cuisine, dishType, methods, sauces);
	}

	private static RecommendationRuleEngine.IngredientSnapshot ingredient(String amount) {
		return new RecommendationRuleEngine.IngredientSnapshot(
				TARGET_ID, "진간장", new BigDecimal(amount), "큰술");
	}

	private static RecommendationRuleEngine.EvidenceSample sample(
			String ratio, double similarity, UUID reviewId, Instant cookedAt) {
		return new RecommendationRuleEngine.EvidenceSample(
				new BigDecimal(ratio), similarity, evidence(reviewId, cookedAt));
	}

	private static RecommendationEvidence evidence(UUID reviewId, Instant cookedAt) {
		return new RecommendationEvidence(
				reviewId, UUID.randomUUID(), "된장찌개", cookedAt, 5);
	}
}
