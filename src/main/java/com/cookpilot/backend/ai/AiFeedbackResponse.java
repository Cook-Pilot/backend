package com.cookpilot.backend.ai;

import java.util.Map;

/**
 * docs/06_tech_stack_and_architecture.md §9 LLM 응답 구조 + mock 여부 플래그.
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
