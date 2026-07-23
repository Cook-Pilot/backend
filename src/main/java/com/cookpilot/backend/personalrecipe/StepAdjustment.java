package com.cookpilot.backend.personalrecipe;

import java.util.UUID;

/**
 * 단계 diff 1건 (API 노출 + 파생 요청 공용).
 *
 * ADD    — originalStepId 없음. insertAfterStepIndex(원본 몇 번 뒤, -1 = 맨 앞) 앵커 필수,
 *          같은 앵커 안에서는 sortOrder 순서.
 * MODIFY — originalStepId 필수. null 필드는 원본 값 유지("흔든다"→"젓는다" 같은 행위 변경도
 *          instruction 오버라이드 한 건이다).
 * REMOVE — originalStepId 필수.
 */
public record StepAdjustment(
		UUID originalStepId,
		AdjustmentType type,
		Integer insertAfterStepIndex,
		int sortOrder,
		String instruction,
		Integer timerSeconds,
		String cautionNote
) {

	static StepAdjustment from(PersonalStepAdjustmentEntity entity) {
		return new StepAdjustment(
				entity.getOriginalStepId(),
				entity.getAdjustmentType(),
				entity.getInsertAfterStepIndex(),
				entity.getSortOrder(),
				entity.getInstruction(),
				entity.getTimerSeconds(),
				entity.getCautionNote()
		);
	}
}
