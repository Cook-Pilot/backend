package com.cookpilot.backend.ai;

/**
 * 안전 규칙·Gemini·fallback이 공통으로 반환하는 내부 답변.
 */
record AiFeedbackAdvice(
		String speechText,
		String screenText,
		AiFeedbackResponse.SuggestedAction suggestedAction,
		String problem
) {
}
