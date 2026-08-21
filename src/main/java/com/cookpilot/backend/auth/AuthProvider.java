package com.cookpilot.backend.auth;

import java.util.Locale;

/**
 * 로그인 제공자. DB 에는 {@link #name()} 문자열로 저장한다(users.provider).
 *
 * APPLE 은 Developer 계정 준비 후 {@link SocialVerifier} 구현과 함께 추가한다.
 */
public enum AuthProvider {

	GOOGLE,
	KAKAO,
	/** 네이버. 카카오와 같은 액세스 토큰 방식({@link NaverVerifier}). */
	NAVER,
	/** 개발자 로그인. 외부 검증이 없으므로 SocialVerifier 가 없다. */
	DEV;

	/** 경로 변수처럼 외부에서 들어온 문자열을 해석한다. Locale 영향을 받지 않게 ROOT 를 쓴다. */
	public static AuthProvider from(String raw) {
		try {
			return valueOf(raw.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new IllegalArgumentException("지원하지 않는 로그인 제공자입니다: " + raw);
		}
	}
}
