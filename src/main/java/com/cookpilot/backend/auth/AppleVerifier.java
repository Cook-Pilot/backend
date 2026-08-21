package com.cookpilot.backend.auth;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jwt.JWTClaimsSet;

/**
 * 애플 로그인(Sign in with Apple). 클라이언트가 받은 identity token(JWT)을 애플 공개키(JWKS)로 검증한다.
 *
 * aud 는 iOS 네이티브 로그인이면 앱 번들 ID, 안드로이드·웹(리다이렉트 방식)이면 Services ID 다.
 * 둘 다 쓰려면 {@code APPLE_CLIENT_IDS} 에 쉼표로 나열한다.
 *
 * 애플은 <b>이름을 토큰에 싣지 않는다</b> — 최초 로그인 1회만 클라이언트에 넘겨준다. 그래서 여기서는
 * 항상 null 을 돌려주고, 클라이언트가 요청 본문 {@code displayName} 으로 보내면 계정 생성 시 쓴다
 * ({@link AuthService#login}). 이메일은 "이메일 가리기"를 고르면 {@code @privaterelay.appleid.com}
 * 중계 주소가 온다 — 그래도 고유하고 전달은 되므로 그대로 저장한다.
 *
 * 탈퇴 시 토큰 revoke(애플 심사 요건)는 아직 없다 — Developer 키(.p8)와 authorization code 교환이
 * 필요해서 {@link SocialUnlinker} 구현과 함께 후속으로 붙인다.
 */
@Component
public class AppleVerifier extends IdTokenVerifier {

	static final String JWKS_URL = "https://appleid.apple.com/auth/keys";
	static final Set<String> ISSUERS = Set.of("https://appleid.apple.com");

	/** 생성자가 둘이라 스프링이 고를 수 있게 표시한다. */
	@Autowired
	public AppleVerifier(@Value("${cookpilot.auth.apple.client-ids:}") String clientIds) {
		this(clientIds, JWKS_URL);
	}

	/** 테스트에서 로컬 JWKS 를 꽂기 위한 생성자. */
	AppleVerifier(String clientIds, String jwksUrl) {
		super("애플", "APPLE_CLIENT_IDS", jwksUrl, ISSUERS, clientIds);
	}

	@Override
	public AuthProvider provider() {
		return AuthProvider.APPLE;
	}

	@Override
	protected String displayName(JWTClaimsSet claims) {
		return null;
	}
}
