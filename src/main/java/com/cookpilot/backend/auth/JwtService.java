package com.cookpilot.backend.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * 우리 세션 토큰. 소셜 제공자의 토큰은 로그인 순간에만 쓰고 버린다 — 이후 모든 요청은 이 토큰으로 인증한다.
 *
 * 서버가 한 대라 대칭키(HS256)로 충분하다. 비대칭키는 검증 주체가 여럿일 때 값어치가 있다.
 * 무상태라 로그아웃/강제 만료가 즉시 되지 않으므로 유효기간을 짧게(기본 14일) 두고 재로그인시킨다.
 */
@Service
public class JwtService {

	private static final String ISSUER = "cookpilot";

	private final byte[] secret;
	private final Duration validity;

	public JwtService(
			@Value("${cookpilot.auth.jwt-secret}") String secret,
			@Value("${cookpilot.auth.jwt-validity:P14D}") Duration validity) {
		// 플랫폼 기본 charset 에 맡기면 런타임 로케일에 따라 키 바이트가 달라져
		// 같은 시크릿인데도 기존 토큰이 전부 무효가 될 수 있다.
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			// HS256 은 키가 짧으면 서명 자체가 거부된다. 기동 시점에 알려야 운영에서 안 터진다.
			throw new IllegalStateException(
					"JWT 서명 키가 너무 짧습니다(최소 32바이트). JWT_SECRET 을 설정하세요: openssl rand -base64 48");
		}
		this.secret = bytes;
		this.validity = validity;
	}

	public IssuedToken issue(UUID userId) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(validity);
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(ISSUER)
				.subject(userId.toString())
				.issueTime(Date.from(now))
				.expirationTime(Date.from(expiresAt))
				.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		try {
			jwt.sign(new MACSigner(secret));
		} catch (JOSEException exception) {
			throw new IllegalStateException("토큰 서명에 실패했습니다.", exception);
		}
		return new IssuedToken(jwt.serialize(), expiresAt);
	}

	/** 유효하지 않으면 {@link InvalidTokenException}. 호출부가 401 로 변환한다. */
	public UUID verify(String token) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!jwt.verify(new MACVerifier(secret))) {
				throw new InvalidTokenException("토큰 서명이 올바르지 않습니다.");
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Date expiration = claims.getExpirationTime();
			if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
				throw new InvalidTokenException("토큰이 만료되었습니다. 다시 로그인해 주세요.");
			}
			if (!ISSUER.equals(claims.getIssuer())) {
				throw new InvalidTokenException("토큰 발급자가 올바르지 않습니다.");
			}
			return UUID.fromString(claims.getSubject());
		} catch (java.text.ParseException | JOSEException | IllegalArgumentException exception) {
			throw new InvalidTokenException("토큰을 해석할 수 없습니다.");
		}
	}

	public record IssuedToken(String token, Instant expiresAt) {
	}
}
