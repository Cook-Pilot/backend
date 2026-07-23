package com.cookpilot.backend.personalrecipe;

import java.util.UUID;

/**
 * 원본 + diff 를 합성한 "완성된 내 단계" 1행. stepIndex 는 합성 결과 기준으로 0부터 재부여된다.
 */
public record ComposedStep(
		int stepIndex,
		UUID originalStepId,
		String instruction,
		Integer timerSeconds,
		String cautionNote,
		Origin origin
) {

	public enum Origin {
		ORIGINAL, MODIFIED, ADDED
	}
}
