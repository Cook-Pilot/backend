package com.cookpilot.backend.auth;

import java.text.ParseException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jwt.JWTClaimsSet;

/**
 * 구글 로그인. 클라이언트가 받은 ID 토큰(JWT)을 구글 공개키(JWKS)로 검증한다.
 *
 * 안드로이드·iOS·웹이 서로 다른 클라이언트 ID 를 쓰므로 {@code GOOGLE_CLIENT_IDS} 에 여러 개를 나열한다.
 * 검증 규칙은 {@link IdTokenVerifier} 참고.
 */
@Component
public class GoogleVerifier extends IdTokenVerifier {

	static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
	/** 구글은 iss 를 두 형태로 발급한다. */
	static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

	/** 생성자가 둘이라 스프링이 고를 수 있게 표시한다. */
	@Autowired
	public GoogleVerifier(@Value("${cookpilot.auth.google.client-ids:}") String clientIds) {
		this(clientIds, JWKS_URL);
	}

	/** 테스트에서 로컬 JWKS 를 꽂기 위한 생성자. */
	GoogleVerifier(String clientIds, String jwksUrl) {
		super("구글", "GOOGLE_CLIENT_IDS", jwksUrl, ISSUERS, clientIds);
	}

	@Override
	public AuthProvider provider() {
		return AuthProvider.GOOGLE;
	}

	@Override
	protected String displayName(JWTClaimsSet claims) throws ParseException {
		return claims.getStringClaim("name");
	}
}
