package com.cookpilot.backend.ai;

public class AiFeedbackRateLimitExceededException extends RuntimeException {

	public AiFeedbackRateLimitExceededException(String message) {
		super(message);
	}
}
