package com.cookpilot.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cookpilot.backend.review.CookingResultPayload.IngredientSnapshot;
import com.cookpilot.backend.review.CookingResultPayload.StepSnapshot;

class PostCookReviewEntityTest {

	@Test
	void 기존_후기_생성자는_FINALIZED와_null_payload를_유지한다() {
		PostCookReviewEntity entity = new PostCookReviewEntity(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				Instant.parse("2026-07-29T01:02:03Z"),
				null,
				new BigDecimal("2"),
				5,
				"맛있어요",
				null);

		assertThat(entity.getReviewStatus())
				.isEqualTo(ReviewLifecycleStatus.FINALIZED);
		assertThat(entity.getCookingResultSchemaVersion()).isNull();
		assertThat(entity.getCookingResultPayload()).isNull();
		assertThat(entity.getCookingResultFingerprint()).isNull();
	}

	@Test
	void pending_factory는_payload와_관계형_식별값을_같은_원본에서_만든다() {
		UUID userId = UUID.randomUUID();
		UUID clientSessionId = UUID.randomUUID();
		CookingResultPayload payload = payload();

		PostCookReviewEntity entity =
				PostCookReviewEntity.pendingCookingResult(
						userId, clientSessionId, payload);

		assertThat(entity.getUserId()).isEqualTo(userId);
		assertThat(entity.getClientSessionId()).isEqualTo(clientSessionId);
		assertThat(entity.getRecipeId()).isEqualTo(payload.recipeId());
		assertThat(entity.getCookedAt()).isEqualTo(payload.cookedAt());
		assertThat(entity.getSourcePersonalVersionId())
				.isEqualTo(payload.sourcePersonalVersionId());
		assertThat(entity.getTargetServings())
				.isEqualTo(payload.targetServings());
		assertThat(entity.getCookingResultSchemaVersion())
				.isEqualTo(CookingResultFingerprint.SCHEMA_VERSION);
		assertThat(entity.getCookingResultPayload()).isSameAs(payload);
		assertThat(entity.getCookingResultFingerprint())
				.isEqualTo(CookingResultFingerprint.sha256(payload));
		assertThat(entity.getReviewStatus())
				.isEqualTo(ReviewLifecycleStatus.PENDING_REVIEW);
		assertThat(entity.getRating()).isNull();
	}

	@Test
	void pending_factory의_식별값과_payload는_필수다() {
		CookingResultPayload payload = payload();

		assertThatThrownBy(() ->
				PostCookReviewEntity.pendingCookingResult(
						null, UUID.randomUUID(), payload))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
				PostCookReviewEntity.pendingCookingResult(
						UUID.randomUUID(), null, payload))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
				PostCookReviewEntity.pendingCookingResult(
						UUID.randomUUID(), UUID.randomUUID(), null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private CookingResultPayload payload() {
		return new CookingResultPayload(
				UUID.randomUUID(),
				Instant.parse("2026-07-29T01:02:03.123456789Z"),
				UUID.randomUUID(),
				new BigDecimal("2.00"),
				List.of(new IngredientSnapshot(
						UUID.randomUUID(), "밥", BigDecimal.ONE,
						"공기", true, false, 0)),
				List.of(new StepSnapshot(
						UUID.randomUUID(), "볶는다", 60, null, 0)));
	}
}
