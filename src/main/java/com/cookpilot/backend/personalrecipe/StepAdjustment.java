package com.cookpilot.backend.personalrecipe;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 단계 diff 1건 (API 노출 + 파생 요청 공용).
 *
 * ADD    — originalStepId 없음. insertAfterStepIndex(원본 몇 번 뒤, -1 = 맨 앞) 앵커 필수,
 *          같은 앵커 안에서는 sortOrder 순서.
 * MODIFY — originalStepId 필수. null 필드는 원본 값 유지("흔든다"→"젓는다" 같은 행위 변경도
 *          instruction 오버라이드 한 건이다).
 * REMOVE — originalStepId 필수.
 *
 * 필드 단독 검증은 어노테이션(요청 경로에서만 동작, 응답 직렬화에는 영향 없음).
 * type 조건부 규칙과 원본 대조는 PersonalRecipeService.validate 소관.
 * instruction 은 MODIFY 에서 null(유지)이 정상이라 @NotBlank 대신 null 허용 @Pattern.
 */
public record StepAdjustment(
		UUID originalStepId,
		@NotNull(message = "단계 조정에 type은 필수입니다.")
		AdjustmentType type,
		@Min(value = -1, message = "단계 조정의 insertAfterStepIndex는 -1 이상이어야 합니다.")
		Integer insertAfterStepIndex,
		int sortOrder,
		@Pattern(regexp = ".*\\P{javaWhitespace}.*", flags = Pattern.Flag.DOTALL,
				message = "단계 조정의 instruction은 공백일 수 없습니다.")
		String instruction,
		@PositiveOrZero(message = "단계 조정의 timerSeconds는 0 이상이어야 합니다.")
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
