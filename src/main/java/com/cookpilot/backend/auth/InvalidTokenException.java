package com.cookpilot.backend.auth;

/** 세션 토큰이 없거나 유효하지 않다. 핸들러가 401 로 매핑한다. */
public class InvalidTokenException extends RuntimeException {

	public InvalidTokenException(String message) {
		super(message);
	}
}
