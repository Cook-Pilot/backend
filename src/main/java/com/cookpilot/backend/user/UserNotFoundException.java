package com.cookpilot.backend.user;

import com.cookpilot.backend.common.NotFoundException;

public class UserNotFoundException extends NotFoundException {

	public UserNotFoundException(String message) {
		super(message);
	}
}
