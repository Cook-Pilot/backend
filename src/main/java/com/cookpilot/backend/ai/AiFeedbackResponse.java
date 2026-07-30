package com.cookpilot.backend.ai;

import java.util.Map;

/**
 * F-08 조리 도움 응답. mock 필드는 기존 클라이언트 호환을 위해 유지하며 실제
 * 안전 규칙·Gemini·fallback 응답은 모두 false다.
 */
public record AiFeedbackResponse(
		boolean mock,
		String speechText,
		String screenText,
		SuggestedAction suggestedAction,
		Map<String, Object> eventPayload
) {

	public record SuggestedAction(String type, Integer seconds) {
	}
}
