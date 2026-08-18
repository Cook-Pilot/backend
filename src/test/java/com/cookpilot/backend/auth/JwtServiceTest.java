package com.cookpilot.backend.auth;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

	private static final String SECRET = "test-secret-that-is-long-enough-for-hs256!!";

	private final JwtService jwtService = new JwtService(SECRET, Duration.ofDays(14));

	@Test
	void 발급한_토큰은_같은_사용자로_검증된다() {
		UUID userId = UUID.randomUUID();

		JwtService.IssuedToken issued = jwtService.issue(userId);

		assertThat(jwtService.verify(issued.token())).isEqualTo(userId);
		assertThat(issued.expiresAt()).isAfter(java.time.Instant.now());
	}

	@Test
	void 다른_키로_서명된_토큰은_거부된다() {
		String forged = new JwtService("another-secret-that-is-long-enough-for-hs256", Duration.ofDays(14))
				.issue(UUID.randomUUID()).token();

		assertThatThrownBy(() -> jwtService.verify(forged))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 만료된_토큰은_거부된다() {
		String expired = new JwtService(SECRET, Duration.ofSeconds(-1)).issue(UUID.randomUUID()).token();

		assertThatThrownBy(() -> jwtService.verify(expired))
				.isInstanceOf(InvalidTokenException.class)
				.hasMessageContaining("만료");
	}

	@Test
	void 토큰이_아닌_문자열은_거부된다() {
		assertThatThrownBy(() -> jwtService.verify("not-a-token"))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 짧은_서명키는_기동_시점에_막는다() {
		assertThatThrownBy(() -> new JwtService("too-short", Duration.ofDays(1)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32바이트");
	}
}
