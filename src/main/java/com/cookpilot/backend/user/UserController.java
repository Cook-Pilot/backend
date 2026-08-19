package com.cookpilot.backend.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	public User me() {
		return userService.getCurrentUser();
	}

	/** 온보딩 프로필 저장. 빈 body({})는 건너뛰기 — 물어봤다는 기록만 남는다. */
	@PatchMapping("/me")
	public User updateProfile(@RequestBody(required = false) UpdateProfileRequest request) {
		return userService.updateProfile(
				request == null ? new UpdateProfileRequest(null, null) : request);
	}

	@PostMapping("/anonymous")
	@ResponseStatus(HttpStatus.CREATED)
	public User createAnonymous(
			@RequestHeader(value = UserService.IDEMPOTENCY_KEY_HEADER, required = false)
			String idempotencyKey) {
		return userService.createAnonymousUser(idempotencyKey);
	}
}
