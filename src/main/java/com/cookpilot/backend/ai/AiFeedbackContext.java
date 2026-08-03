package com.cookpilot.backend.ai;

import java.util.UUID;

/**
 * F-08 답변 생성에 필요한, 프론트가 소유한 현재 조리 맥락.
 *
 * 서버는 조리 세션을 저장하지 않는다. 레시피 존재 여부를 확인한 뒤 현재 실행 중인
 * 단계 설명과 타이머를 이 값으로 Gemini에 전달한다. 실행 instruction이 없는 구버전
 * 요청에 한해서만 원본 레시피의 같은 단계 존재 여부도 확인한다.
 */
record AiFeedbackContext(
		UUID recipeId,
		String recipeTitle,
		int stepIndex,
		String instruction,
		Integer remainingSeconds,
		String userSpeech
) {
}
