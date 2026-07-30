package com.cookpilot.backend.ai;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFeedbackRateLimiterTest {

	@Test
	void 같은_사용자는_분당_한도를_넘으면_거부하고_다른_사용자는_분리한다() {
		AiFeedbackRateLimiter limiter = new AiFeedbackRateLimiter(
				new AiFeedbackRateLimitProperties(2),
				Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC));
		UUID firstUser = UUID.randomUUID();
		UUID secondUser = UUID.randomUUID();

		limiter.acquire(firstUser);
		limiter.acquire(firstUser);

		assertThatThrownBy(() -> limiter.acquire(firstUser))
				.isInstanceOf(AiFeedbackRateLimitExceededException.class);
		assertThatNoException().isThrownBy(() -> limiter.acquire(secondUser));
	}
}
