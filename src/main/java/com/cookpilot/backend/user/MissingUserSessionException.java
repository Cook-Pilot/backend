package com.cookpilot.backend.user;

public class MissingUserSessionException extends RuntimeException {

	public MissingUserSessionException(String message) {
		super(message);
	}
}
