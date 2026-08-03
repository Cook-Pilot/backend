package com.cookpilot.backend.ai;

/** Advisor 체인에서 모델 호출을 차단하고 서버 소유 안전 답변을 전달한다. */
final class AiFeedbackSafetyDecisionException extends RuntimeException {

	private final AiFeedbackAdvice advice;

	AiFeedbackSafetyDecisionException(AiFeedbackAdvice advice) {
		super(null, null, false, false);
		this.advice = advice;
	}

	AiFeedbackAdvice advice() {
		return advice;
	}
}
