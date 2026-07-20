package com.cookpilot.backend.user;

import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * 인증 방식 미확정. MVP에서는 고정 목유저 1명을 가정한다.
 */
@Service
public class UserService {

	private static final User MOCK_USER = new User(
			UUID.fromString("00000000-0000-0000-0000-000000000001"),
			"demo@cookpilot.app",
			"데모 사용자"
	);

	public User getCurrentUser() {
		return MOCK_USER;
	}
}
