package com.cookpilot.backend.auth;

/**
 * 회원 탈퇴 시 소셜 제공자 쪽 연결을 끊는다. 제공자마다 하나씩 구현하고
 * {@link com.cookpilot.backend.user.AccountDeletionService} 가 사용자의 provider 로 골라 부른다.
 *
 * 구현은 best-effort 여야 한다 — 제공자 장애가 우리 쪽 삭제(방침 제9조)를 막으면 안 되므로
 * 예외를 밖으로 던지지 말고 로그만 남긴다.
 *
 * 지금은 {@link KakaoUnlinker} 뿐이다(카카오 개발자 정책이 unlink 를 요구). 구글은 정책 요구가
 * 없어 구현하지 않았다. APPLE 은 심사 요건상 revoke 가 필요하지만 Developer 키(.p8)로 만든
 * client_secret 과 로그인 시 받은 authorization code 교환이 있어야 해서 아직 없다 — 후속 과제.
 */
public interface SocialUnlinker {

	/** 이 구현이 담당하는 제공자. */
	AuthProvider provider();

	/** 제공자 쪽 연결 해제. 실패해도 예외를 던지지 않는다. */
	void unlink(String providerUserId);
}
