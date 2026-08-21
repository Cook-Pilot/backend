package com.cookpilot.backend.auth;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.util.StringUtils;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/**
 * OIDC ID 토큰(JWT)을 제공자 공개키(JWKS)로 검증하는 공통 골격. 구글·애플이 같은 방식이다.
 *
 * aud(대상) 검사가 핵심이다 — 이게 없으면 다른 앱용으로 발급된 정상 토큰까지 통과해서,
 * 아무 앱에서나 받은 토큰으로 우리 계정에 로그인할 수 있다. 플랫폼마다 클라이언트 ID 가
 * 다르므로 여러 개를 허용한다.
 *
 * 하위 클래스는 제공자 상수(JWKS 주소·발급자)와 이름 클레임 해석만 채운다.
 */
abstract class IdTokenVerifier implements SocialVerifier {

	private final String label;
	private final String configHint;
	private final Set<String> issuers;
	private final Set<String> allowedAudiences;
	private final DefaultJWTProcessor<SecurityContext> processor;

	/**
	 * @param label      오류 문구에 들어갈 제공자 이름("구글"·"애플")
	 * @param configHint 설정 누락 시 로그에 남길 환경변수 이름
	 * @param jwksUrl    제공자 공개키 주소. 키는 캐시되고 제공자가 로테이션하면 자동으로 다시 받아온다.
	 * @param issuers    허용하는 iss 값들
	 * @param clientIds  쉼표로 나열한 허용 aud 값들
	 */
	protected IdTokenVerifier(
			String label, String configHint, String jwksUrl, Set<String> issuers, String clientIds) {
		this.label = label;
		this.configHint = configHint;
		this.issuers = Set.copyOf(issuers);
		// .env 에 "a, b" 처럼 쉼표 뒤 공백을 넣어도 조용히 깨지지 않게 다듬는다.
		this.allowedAudiences = Set.copyOf(StringUtils.commaDelimitedListToSet(clientIds).stream()
				.map(String::strip).filter(StringUtils::hasText).toList());
		this.processor = new DefaultJWTProcessor<>();
		try {
			processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
					JWSAlgorithm.RS256,
					JWKSourceBuilder.create(URI.create(jwksUrl).toURL()).build()));
		} catch (MalformedURLException exception) {
			throw new IllegalStateException(label + " JWKS 주소가 올바르지 않습니다.", exception);
		}
	}

	@Override
	public SocialIdentity verify(String token) {
		if (allowedAudiences.isEmpty()) {
			// IllegalStateException 은 핸들러가 409 로 매핑한다 — 설정 누락은 서버 오류(500)다.
			throw new ProviderNotConfiguredException(
					label + " 클라이언트 ID 가 없습니다. " + configHint + " 를 설정하세요.");
		}
		JWTClaimsSet claims;
		try {
			claims = processor.process(token, null);
		} catch (Exception exception) {
			throw new InvalidTokenException(label + " 토큰을 검증하지 못했습니다.");
		}

		if (!issuers.contains(claims.getIssuer())) {
			throw new InvalidTokenException(label + " 토큰의 발급자가 올바르지 않습니다.");
		}
		List<String> audiences = claims.getAudience();
		if (audiences == null || audiences.stream().noneMatch(allowedAudiences::contains)) {
			throw new InvalidTokenException("이 앱을 위해 발급된 " + label + " 토큰이 아닙니다.");
		}
		Date expiration = claims.getExpirationTime();
		if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
			throw new InvalidTokenException(label + " 토큰이 만료되었습니다.");
		}

		try {
			String subject = claims.getSubject();
			if (!StringUtils.hasText(subject)) {
				throw new InvalidTokenException(label + " 토큰에 계정 식별자가 없습니다.");
			}
			return new SocialIdentity(provider(), subject, claims.getStringClaim("email"), displayName(claims));
		} catch (ParseException exception) {
			throw new InvalidTokenException(label + " 토큰의 사용자 정보를 읽지 못했습니다.");
		}
	}

	/** 토큰에서 표시 이름을 꺼낸다. 제공자가 이름을 토큰에 싣지 않으면 null. */
	protected abstract String displayName(JWTClaimsSet claims) throws ParseException;
}
