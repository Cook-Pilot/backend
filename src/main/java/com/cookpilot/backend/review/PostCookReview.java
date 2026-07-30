package com.cookpilot.backend.review;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PostCookReview(
		UUID id,
		UUID userId,
		UUID recipeId,
		UUID clientSessionId,
		Instant cookedAt,
		UUID sourcePersonalVersionId,
		BigDecimal targetServings,
		Integer rating,
		String comment,
		String nextTimeNote,
		UUID createdPersonalVersionId,
		Instant createdAt
) {

	/**
	 * createdPersonalVersionId 는 이 리뷰에서 파생된 개인 버전이다. 리뷰 저장 시점엔 항상 null —
	 * 버전은 수정 파이프라인이 나중에 만들고 source_review_id 로 이 리뷰를 역참조한다.
	 * 조회 시에만 그 역참조를 따라가 채운다.
	 */
	static PostCookReview from(PostCookReviewEntity entity, UUID createdPersonalVersionId) {
		return new PostCookReview(
				entity.getId(),
				entity.getUserId(),
				entity.getRecipeId(),
				entity.getClientSessionId(),
				entity.getCookedAt(),
				entity.getSourcePersonalVersionId(),
				entity.getTargetServings(),
				entity.getRating(),
				entity.getComment(),
				entity.getNextTimeNote(),
				createdPersonalVersionId,
				entity.getCreatedAt());
	}
}
