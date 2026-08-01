package com.cookpilot.backend.personalrecipe;

import java.util.Map;
import java.util.UUID;

/**
 * 층 함수들이 공유하는 원본 레시피 스냅샷. 원본은 불변이므로 한 번 읽어 층마다 돌려 쓴다.
 *
 * diff 의 MODIFY/REMOVE 가 가리키는 원본 행을 id 로 찾아야 하므로 Map 이다.
 * DiffComposer 로 합성할 때는 values() 를 넘기면 된다 — 합성기가 안에서 정렬한다.
 */
record Originals(Map<UUID, DiffComposer.OriginalIngredient> ingredients,
		Map<UUID, DiffComposer.OriginalStep> steps) {
}
