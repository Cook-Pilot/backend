package com.cookpilot.backend.ai;

/** F-08 모델 또는 advisor 실패를 deterministic fallback 경계로 전달한다. */
final class AiFeedbackGenerationException extends RuntimeException {

	AiFeedbackGenerationException(RuntimeException cause) {
		super(null, cause, false, false);
	}
}
