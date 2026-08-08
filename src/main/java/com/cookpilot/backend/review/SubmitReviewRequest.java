package com.cookpilot.backend.review;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 조리 후 리뷰 저장 요청. 필드는 post_cook_reviews 컬럼과 1:1 이다.
 *
 * 실행 스냅샷(재료·단계)은 여기서 받지 않는다 — 개인 레시피 버전 생성은 별도 경로다.
 * 이 요청은 "무엇을 조리했고 어땠는지"만 기록한다.
 *
 * 서버가 채우는 컬럼: id, user_id, created_at, structured_feedback.
 */
public record SubmitReviewRequest(
		@NotNull(message = "clientSessionId는 필수입니다.") UUID clientSessionId,
		@NotNull(message = "recipeId는 필수입니다.") UUID recipeId,
		Instant cookedAt,
		BigDecimal targetServings,
		UUID sourcePersonalVersionId,
		@NotNull(message = "rating은 필수입니다.") Integer rating,
		String comment,
		String nextTimeNote,
		// 사진은 외부 스토리지 URL만 받는다(파일 업로드 없음). null = 사진 없음.
		@Size(max = 10, message = "photoUrls는 최대 10장입니다.")
		List<@NotBlank(message = "photoUrls 항목은 비어 있을 수 없습니다.") String> photoUrls
) {
}
