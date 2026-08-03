package com.cookpilot.backend.ai;

import java.util.Optional;

/**
 * 외부 답변 생성기 경계. Gemini가 꺼졌거나 호출/검증에 실패하면 empty를 반환한다.
 */
interface AiFeedbackClient {

	Optional<AiFeedbackAdvice> advise(AiFeedbackContext context);
}
