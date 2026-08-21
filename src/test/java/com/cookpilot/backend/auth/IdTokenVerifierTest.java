package com.cookpilot.backend.auth;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구글·애플 ID 토큰 검증. 실제 제공자 대신 로컬 HTTP 서버가 테스트용 RSA 공개키(JWKS)를 내주고,
 * 같은 키로 서명한 토큰을 넣어 본다 — 서명·발급자·대상·만료 검사가 전부 실제 코드 경로를 탄다.
 * Spring 컨텍스트도 Docker 도 필요 없다.
 */
class IdTokenVerifierTest {

	private static final String APPLE_AUD = "com.cookpilot.cookpilot";
	private static final String GOOGLE_AUD = "123-abc.apps.googleusercontent.com";

	private static HttpServer jwksServer;
	private static String jwksUrl;
	private static RSAKey signingKey;
	private static RSAKey strangerKey;

	@BeforeAll
	static void startJwks() throws Exception {
		signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
		strangerKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
		byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

		jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		jwksServer.createContext("/keys", exchange -> {
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, jwks.length);
			try (OutputStream body = exchange.getResponseBody()) {
				body.write(jwks);
			}
		});
		jwksServer.start();
		jwksUrl = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/keys";
	}

	@AfterAll
	static void stopJwks() {
		jwksServer.stop(0);
	}

	@Test
	void 애플_토큰은_sub와_email만_주고_이름은_없다() throws Exception {
		AppleVerifier verifier = new AppleVerifier(APPLE_AUD, jwksUrl);

		SocialIdentity identity = verifier.verify(sign(signingKey, appleClaims(builder -> {})));

		assertThat(identity.provider()).isEqualTo(AuthProvider.APPLE);
		assertThat(identity.providerUserId()).isEqualTo("001234.abcdef.5678");
		assertThat(identity.email()).isEqualTo("hidden@privaterelay.appleid.com");
		// 애플은 이름을 토큰에 싣지 않는다. 클라이언트가 따로 넘긴 displayName 은 AuthService 가 처리한다.
		assertThat(identity.displayName()).isNull();
	}

	@Test
	void 구글_토큰은_name_클레임을_표시_이름으로_쓴다() throws Exception {
		GoogleVerifier verifier = new GoogleVerifier(GOOGLE_AUD, jwksUrl);

		SocialIdentity identity = verifier.verify(sign(signingKey, googleClaims("https://accounts.google.com")));

		assertThat(identity.provider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(identity.providerUserId()).isEqualTo("10769150350006150715113082367");
		assertThat(identity.email()).isEqualTo("user@example.com");
		assertThat(identity.displayName()).isEqualTo("홍길동");
	}

	@Test
	void 구글은_짧은_발급자_표기도_허용한다() throws Exception {
		GoogleVerifier verifier = new GoogleVerifier(GOOGLE_AUD, jwksUrl);

		assertThat(verifier.verify(sign(signingKey, googleClaims("accounts.google.com"))).providerUserId())
				.isNotBlank();
	}

	@Test
	void 여러_클라이언트_ID_중_하나만_맞아도_통과한다() throws Exception {
		AppleVerifier verifier = new AppleVerifier("com.cookpilot.web, " + APPLE_AUD, jwksUrl);

		assertThat(verifier.verify(sign(signingKey, appleClaims(builder -> {}))).providerUserId()).isNotBlank();
	}

	@Test
	void 다른_앱용으로_발급된_토큰은_거부한다() throws Exception {
		AppleVerifier verifier = new AppleVerifier(APPLE_AUD, jwksUrl);
		String token = sign(signingKey, appleClaims(builder -> builder.audience("com.someone.else")));

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(InvalidTokenException.class)
				.hasMessageContaining("이 앱을 위해 발급된");
	}

	@Test
	void 발급자가_다르면_거부한다() throws Exception {
		AppleVerifier verifier = new AppleVerifier(APPLE_AUD, jwksUrl);
		String token = sign(signingKey, appleClaims(builder -> builder.issuer("https://accounts.google.com")));

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(InvalidTokenException.class)
				.hasMessageContaining("발급자");
	}

	@Test
	void 발급자_클레임이_없으면_NPE가_아니라_거부다() throws Exception {
		// Set.copyOf 불변 집합의 contains(null) 은 NPE 다 — 500 이 아니라 401 로 내려가야 한다.
		AppleVerifier verifier = new AppleVerifier(APPLE_AUD, jwksUrl);
		String token = sign(signingKey, appleClaims(builder -> builder.issuer(null)));

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(InvalidTokenException.class)
				.hasMessageContaining("발급자");
	}

	@Test
	void 대상_클레임이_없으면_거부한다() throws Exception {
		AppleVerifier verifier = new AppleVerifier(APPLE_AUD, jwksUrl);
		String token = sign(signingKey, appleClaims(builder -> builder.audience((String) null)));

		assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 만료된_토큰은_거부한다() throws Exception {
		AppleVerifier verifier = new AppleVerifier(APPLE_AUD, jwksUrl);
		String token = sign(signingKey, appleClaims(builder ->
				builder.expirationTime(Date.from(Instant.now().minusSeconds(3600)))));

		assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 다른_키로_서명된_토큰은_거부한다() throws Exception {
		// kid 는 같지만 키가 다르다 — JWKS 의 공개키로 서명이 맞지 않아야 한다.
		AppleVerifier verifier = new AppleVerifier(APPLE_AUD, jwksUrl);
		String token = sign(strangerKey, appleClaims(builder -> {}));

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(InvalidTokenException.class)
				.hasMessageContaining("검증하지 못했습니다");
	}

	@Test
	void 계정_식별자가_없으면_거부한다() throws Exception {
		AppleVerifier verifier = new AppleVerifier(APPLE_AUD, jwksUrl);
		String token = sign(signingKey, appleClaims(builder -> builder.subject(null)));

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(InvalidTokenException.class)
				.hasMessageContaining("계정 식별자");
	}

	@Test
	void JWT가_아닌_문자열은_거부한다() {
		AppleVerifier verifier = new AppleVerifier(APPLE_AUD, jwksUrl);

		assertThatThrownBy(() -> verifier.verify("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 클라이언트_ID가_없으면_설정_오류다() throws Exception {
		// 토큰이 멀쩡해도 설정이 없으면 서버 쪽 문제(500)로 분류돼야 한다 — 클라이언트 잘못(401)이 아니다.
		AppleVerifier verifier = new AppleVerifier("", jwksUrl);
		String token = sign(signingKey, appleClaims(builder -> {}));

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(ProviderNotConfiguredException.class)
				.hasMessageContaining("APPLE_CLIENT_IDS");
	}

	private static JWTClaimsSet appleClaims(Consumer<JWTClaimsSet.Builder> customizer) {
		JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
				.issuer("https://appleid.apple.com")
				.audience(APPLE_AUD)
				.subject("001234.abcdef.5678")
				.claim("email", "hidden@privaterelay.appleid.com")
				.claim("email_verified", "true")
				.claim("is_private_email", "true")
				.issueTime(Date.from(Instant.now()))
				.expirationTime(Date.from(Instant.now().plusSeconds(600)));
		customizer.accept(builder);
		return builder.build();
	}

	private static JWTClaimsSet googleClaims(String issuer) {
		return new JWTClaimsSet.Builder()
				.issuer(issuer)
				.audience(GOOGLE_AUD)
				.subject("10769150350006150715113082367")
				.claim("email", "user@example.com")
				.claim("name", "홍길동")
				.issueTime(Date.from(Instant.now()))
				.expirationTime(Date.from(Instant.now().plusSeconds(600)))
				.build();
	}

	private static String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.keyID(key.getKeyID())
				.type(JOSEObjectType.JWT)
				.build();
		SignedJWT jwt = new SignedJWT(header, claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}
}
