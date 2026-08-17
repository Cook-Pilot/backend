package com.cookpilot.backend.auth;

/**
 * 클라이언트가 보낸 제공자 토큰을 검증해 신원을 돌려준다.
 *
 * 검증을 서버에서 하는 게 핵심이다 — 클라이언트가 "나 이 사람이야"라고 주장한 값을 믿으면
 * 아무나 남의 계정으로 로그인할 수 있다.
 */
public interface SocialVerifier {

	/** 이 검증기가 담당하는 제공자. */
	AuthProvider provider();

	SocialIdentity verify(String token);
}
