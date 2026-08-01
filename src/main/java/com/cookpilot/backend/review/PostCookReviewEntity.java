package com.cookpilot.backend.review;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * post_cook_reviews 테이블 매핑(그룹 A 구조).
 *
 * 조리 진행은 프론트가 관리하므로 서버에는 조리 1회의 "결과"인 이 리뷰만 남는다.
 *
 * rating/comment/next_time_note 는 사용자 입력분(그룹 A).
 * structured_feedback(JSONB)는 리뷰를 AI가 구조화한 산출물이라 내부 구조는
 * AI 파트 확정 대상(그룹 B). 여기서는 opaque Map 으로만 매핑한다.
 */
@Entity
@Table(name = "post_cook_reviews")
@Getter
public class PostCookReviewEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	@Column(name = "client_session_id")
	private UUID clientSessionId;

	@Column(name = "cooked_at", nullable = false)
	private Instant cookedAt;

	@Column(name = "source_personal_version_id")
	private UUID sourcePersonalVersionId;

	@Column(name = "target_servings")
	private BigDecimal targetServings;

	// DB의 CHECK (rating BETWEEN 1 AND 5)와 짝을 이룬다. null(무평점)은 허용, 값이 있으면 1~5.
	@Min(1)
	@Max(5)
	@Column(name = "rating")
	private Integer rating;

	@Column(name = "comment")
	private String comment;

	@Column(name = "next_time_note")
	private String nextTimeNote;

	@Setter
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "structured_feedback", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> structuredFeedback = new HashMap<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PostCookReviewEntity() {
	}

	/**
	 * 저장 요청을 그대로 받아 엔티티를 만든다. SubmitReviewRequest 필드는 이 테이블 컬럼과 1:1 이라
	 * 서비스가 인자를 하나씩 풀어 옮길 이유가 없다.
	 *
	 * userId 는 요청이 정하지 않는다(현재 사용자에서 온다). cookedAt 이 비면 수신 시각으로 대체한다 —
	 * DB 가 NOT NULL 이고, 시각을 모른 채 저장하는 것보다 "서버가 받은 때"가 낫다.
	 */
	public static PostCookReviewEntity of(UUID userId, SubmitReviewRequest request) {
		return new PostCookReviewEntity(
				userId,
				request.recipeId(),
				request.clientSessionId(),
				request.cookedAt() != null ? request.cookedAt() : Instant.now(),
				request.sourcePersonalVersionId(),
				request.targetServings(),
				request.rating(),
				request.comment(),
				request.nextTimeNote());
	}

	public PostCookReviewEntity(UUID userId, UUID recipeId, UUID clientSessionId,
			Instant cookedAt, UUID sourcePersonalVersionId, BigDecimal targetServings,
			Integer rating, String comment, String nextTimeNote) {
		this.userId = userId;
		this.recipeId = recipeId;
		this.clientSessionId = clientSessionId;
		this.cookedAt = cookedAt;
		this.sourcePersonalVersionId = sourcePersonalVersionId;
		this.targetServings = targetServings;
		this.rating = rating;
		this.comment = comment;
		this.nextTimeNote = nextTimeNote;
	}

	/** 테스트·기존 내부 호출 호환용. 새 API 저장은 clientSessionId를 포함한 생성자를 사용한다. */
	public PostCookReviewEntity(UUID userId, UUID recipeId, Integer rating,
			String comment, String nextTimeNote) {
		this(userId, recipeId, null, Instant.now(), null, null, rating, comment, nextTimeNote);
	}
}
