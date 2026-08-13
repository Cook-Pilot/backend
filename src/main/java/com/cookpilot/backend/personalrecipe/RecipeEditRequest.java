package com.cookpilot.backend.personalrecipe;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 한 번의 조리에서 나온 레시피 수정 요청 전체.
 *
 * 층 모델(원본 → setup → cooking → review)의 입력이다. 각 층은 원본 레시피 기준의
 * 완결 diff 를 만들고 다음 층이 그것을 이어받는다. 층의 입력이 비어 있으면(침묵)
 * 앞 층 결과가 그대로 살아남는다.
 *
 * setup   — UI 편집에서 나온 정형 diff. LLM 을 거치지 않는다.
 * cooking — 조리 중 발화. 지금은 text 한 덩어리로 받는다.
 * review  — 조리 후 리뷰. 본문에 없다(아래).
 *
 * 본문은 어디에도 저장돼 있지 않은 것만 담는다. 조리 1회의 사실(레시피·인분·부모 버전·
 * 리뷰 본문)은 경로가 지목하는 post_cook_reviews 행이 이미 갖고 있고, 서버가 거기서 읽는다.
 * 같은 사실을 본문으로도 받으면 쓰기 가능한 사본이 둘이 되고, 어긋나도 에러가 나지 않는다 —
 * 리뷰는 4인분인데 본문은 3인분이면 양이 조용히 어긋난 채 base 기준으로 저장된다.
 *
 * 그래서 남는 층은 둘이다. setup 은 UI 편집 결과라 저장된 적이 없고, cooking 은 조리 중
 * 발화라 마찬가지다. review 층의 입력(comment / next_time_note)은 리뷰 행에서 읽는다.
 */
public record RecipeEditRequest(
		@Valid Setup setup,
		Cooking cooking
) {

	/**
	 * 조리 전 UI 편집 결과. diff 는 항상 원본 레시피 기준 누적 전체 집합이다.
	 *
	 * 개인 버전 위에서 편집했더라도 그 버전 대비 델타가 아니라 원본 대비 최종 상태를 보낸다.
	 * 프론트는 화면 각 행의 정체(원본/수정/추가)를 알고 있으므로 머지 후 보낼 수 있고,
	 * 그래야 "이 diff 행을 취소" 같은 두 번째 어휘 없이 부재 = 취소로 끝난다.
	 *
	 * amount 는 조리 인분 기준으로 온다. 서버가 리뷰의 target_servings 로 1인분 기준까지
	 * 되돌려 저장한다 — 인분 자체는 diff 에 들어가지 않는다(레시피 속성이 아니라 실행 파라미터).
	 */
	public record Setup(
			List<@Valid @NotNull(message = "재료 조정에 빈 항목이 있습니다.") IngredientAdjustmentInput> ingredientAdjustments,
			List<@Valid @NotNull(message = "단계 조정에 빈 항목이 있습니다.") StepAdjustment> stepAdjustments
	) {

		public List<IngredientAdjustment> ingredientAdjustmentsOrEmpty() {
			return ingredientAdjustments == null
					? List.of()
					: ingredientAdjustments.stream()
							.map(IngredientAdjustmentInput::toAdjustment)
							.toList();
		}

		public List<StepAdjustment> stepAdjustmentsOrEmpty() {
			return stepAdjustments == null ? List.of() : stepAdjustments;
		}
	}

	/**
	 * 조리 중 대화. transcript 는 날것의 발화 로그이거나 클라이언트가 1차 요약한 문장이다.
	 *
	 * TODO(발화 업로드 경로 확정 후): 화자·타임스탬프·단계 인덱스를 가진 turn 리스트로 확장.
	 */
	public record Cooking(String transcript) {

		public boolean isBlank() {
			return transcript == null || transcript.isBlank();
		}
	}

	/** setup 층이 만들 diff 가 있는지. */
	public boolean hasSetupEdits() {
		return setup != null
				&& !(setup.ingredientAdjustmentsOrEmpty().isEmpty() && setup.stepAdjustmentsOrEmpty().isEmpty());
	}

	/** cooking 층 LLM 호출이 필요한지. 자연어가 없으면 층을 통째로 건너뛴다. */
	public boolean hasCookingTranscript() {
		return cooking != null && !cooking.isBlank();
	}
}
