package com.cookpilot.backend.review;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 조리 후 리뷰 저장 요청. 필드는 post_cook_reviews 컬럼과 1:1 이다.
 *
 * 실행 스냅샷(재료·단계)은 여기서 받지 않는다 — 개인 레시피 버전 생성은 별도 경로다.
 * 이 요청은 "무엇을 조리했고 어땠는지"만 기록한다.
 *
 * 서버가 채우는 컬럼: id, user_id, created_at, structured_feedback.
 */
public record SubmitReviewRequest(
		UUID clientSessionId,
		UUID recipeId,
		Instant cookedAt,
		BigDecimal targetServings,
		UUID sourcePersonalVersionId,
		Integer rating,
		String comment,
		String nextTimeNote
) {
}
