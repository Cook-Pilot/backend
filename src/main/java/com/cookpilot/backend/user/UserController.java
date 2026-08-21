package com.cookpilot.backend.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserService userService;
	private final AccountDeletionService accountDeletionService;

	public UserController(UserService userService, AccountDeletionService accountDeletionService) {
		this.userService = userService;
		this.accountDeletionService = accountDeletionService;
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

	/**
	 * 계정 삭제(회원 탈퇴). 후기·사진·개인 버전·즐겨찾기·추천 반응이 모두 함께 삭제된다.
	 * 재호출은 404 (이미 삭제됨) — 멱등. 상세는 {@link AccountDeletionService}.
	 *
	 * 신원은 세션 토큰(Authorization: Bearer)에서만 나온다 — 비가역 삭제라 식별자 헤더만으로
	 * 통과되면 안 된다. 토큰이 없거나 잘못되면 {@link UserService} 가 401 을 낸다.
	 */
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMe() {
		accountDeletionService.deleteCurrentAccount();
	}
}
