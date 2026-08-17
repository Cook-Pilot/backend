package com.cookpilot.backend.auth;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/**
 * 구글 로그인. 클라이언트가 받은 ID 토큰(JWT)을 구글 공개키(JWKS)로 검증한다.
 *
 * aud(대상) 검사가 핵심이다 — 이게 없으면 다른 앱용으로 발급된 정상 토큰까지 통과해서,
 * 아무 구글 앱에서나 받은 토큰으로 우리 계정에 로그인할 수 있다.
 * 안드로이드·iOS·웹이 서로 다른 클라이언트 ID 를 쓰므로 여러 개를 허용한다.
 */
@Component
public class GoogleVerifier implements SocialVerifier {

	private static final String ISSUER = "https://accounts.google.com";
	private static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";

	private final DefaultJWTProcessor<SecurityContext> processor;
	private final Set<String> allowedAudiences;

	public GoogleVerifier(@Value("${cookpilot.auth.google.client-ids:}") String clientIds) {
		this.allowedAudiences = Set.copyOf(
				StringUtils.commaDelimitedListToSet(clientIds).stream().filter(StringUtils::hasText).toList());
		this.processor = new DefaultJWTProcessor<>();
		try {
			processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
					JWSAlgorithm.RS256,
					// 키는 캐시되고 구글이 로테이션하면 자동으로 다시 받아온다.
					JWKSourceBuilder.create(URI.create(JWKS_URL).toURL()).build()));
		} catch (MalformedURLException exception) {
			throw new IllegalStateException("구글 JWKS 주소가 올바르지 않습니다.", exception);
		}
	}

	@Override
	public AuthProvider provider() {
		return AuthProvider.GOOGLE;
	}

	@Override
	public SocialIdentity verify(String token) {
		if (allowedAudiences.isEmpty()) {
			throw new IllegalStateException(
					"구글 클라이언트 ID 가 없습니다. GOOGLE_CLIENT_IDS 를 설정하세요.");
		}
		JWTClaimsSet claims;
		try {
			claims = processor.process(token, null);
		} catch (Exception exception) {
			throw new InvalidTokenException("구글 토큰을 검증하지 못했습니다.");
		}

		String issuer = claims.getIssuer();
		if (!ISSUER.equals(issuer) && !"accounts.google.com".equals(issuer)) {
			throw new InvalidTokenException("구글 토큰의 발급자가 올바르지 않습니다.");
		}
		List<String> audiences = claims.getAudience();
		if (audiences == null || audiences.stream().noneMatch(allowedAudiences::contains)) {
			throw new InvalidTokenException("이 앱을 위해 발급된 구글 토큰이 아닙니다.");
		}
		Date expiration = claims.getExpirationTime();
		if (expiration == null || expiration.toInstant().isBefore(java.time.Instant.now())) {
			throw new InvalidTokenException("구글 토큰이 만료되었습니다.");
		}

		try {
			String subject = claims.getSubject();
			if (!StringUtils.hasText(subject)) {
				throw new InvalidTokenException("구글 토큰에 계정 식별자가 없습니다.");
			}
			return new SocialIdentity(
					provider(), subject, claims.getStringClaim("email"), claims.getStringClaim("name"));
		} catch (ParseException exception) {
			throw new InvalidTokenException("구글 토큰의 사용자 정보를 읽지 못했습니다.");
		}
	}
}
