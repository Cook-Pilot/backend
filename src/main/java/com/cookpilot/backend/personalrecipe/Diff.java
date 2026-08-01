package com.cookpilot.backend.personalrecipe;

import java.util.List;

/**
 * 층 사이를 흐르는 값. 원본 레시피 기준 누적 diff 전체다.
 *
 * 각 층(setup → cooking → review)은 이것을 받아 이것을 낸다. 층 사이 특별한 중간 타입은
 * 없고, 저장 타입도 같다 — 어휘가 둘이 되면 매핑 코드와 매핑 버그가 생긴다.
 *
 * 부재 = 취소다. 앞 층의 조정을 되돌리려면 그 행을 결과에 넣지 않으면 된다.
 */
record Diff(List<IngredientAdjustment> ingredients, List<StepAdjustment> steps) {

	static final Diff EMPTY = new Diff(List.of(), List.of());

	/** 원본과 다를 게 없다는 뜻. 버전을 만들지 않는다. */
	boolean isEmpty() {
		return ingredients.isEmpty() && steps.isEmpty();
	}
}
