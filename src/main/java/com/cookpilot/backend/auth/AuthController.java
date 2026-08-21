package com.cookpilot.backend.auth;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/**
	 * 소셜 로그인. {@code provider} 는 google / kakao / naver (애플은 계정 준비 후 추가).
	 * 계정이 없으면 이 시점에 만들어진다 — 별도 회원가입 단계가 없다.
	 */
	@PostMapping("/{provider}")
	public AuthResponse login(@PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
		return authService.login(AuthProvider.from(provider), request.token());
	}

	/** 개발자 로그인. 서버에 시크릿이 설정돼 있을 때만 동작한다. */
	@PostMapping("/dev")
	public AuthResponse loginAsDeveloper(@Valid @RequestBody DevLoginRequest request) {
		return authService.loginAsDeveloper(request.secret());
	}
}
