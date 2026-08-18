package com.cookpilot.backend.auth;

/**
 * 제공자에게서 확인한 신원. 제공자마다 응답 형태가 달라도 여기까지 오면 형태가 같다.
 *
 * @param provider       로그인 제공자
 * @param providerUserId 제공자가 부여한 계정 식별자. 이메일과 달리 바뀌지 않는다.
 * @param email          없을 수 있다 — 카카오는 이메일 제공이 선택 동의고, 애플은 가리기가 가능하다.
 * @param displayName    없을 수 있다.
 */
public record SocialIdentity(AuthProvider provider, String providerUserId, String email, String displayName) {
}
