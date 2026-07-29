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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 조리 진행은 프론트가 관리한다. 서버에는 완료 결과를 먼저 보존하고,
 * reviewStatus로 후기 작성 여부를 분리한다.
 *
 * rating/comment/next_time_note 는 사용자 입력분(그룹 A).
 * structured_feedback(JSONB)는 AI 파트 확정 대상(그룹 B)이라 여기서는
 * opaque Map 으로만 매핑한다.
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

	@Column(name = "cooking_result_schema_version", updatable = false)
	private Short cookingResultSchemaVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "cooking_result_payload", columnDefinition = "jsonb", updatable = false)
	private CookingResultPayload cookingResultPayload;

	@Column(name = "cooking_result_fingerprint", updatable = false)
	private String cookingResultFingerprint;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_status", nullable = false)
	private ReviewLifecycleStatus reviewStatus = ReviewLifecycleStatus.FINALIZED;

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

	/**
	 * 완료 결과만 먼저 저장할 엔티티를 만든다.
	 *
	 * <p>관계형 식별 필드와 JSONB payload를 한 payload에서 복사해 서로 어긋날
	 * 수 없게 한다. 개인 레시피 diff나 후기는 이 단계에서 만들지 않는다.</p>
	 */
	public static PostCookReviewEntity pendingCookingResult(
			UUID userId, UUID clientSessionId, CookingResultPayload payload) {
		if (userId == null || clientSessionId == null || payload == null) {
			throw new IllegalArgumentException(
					"userId, clientSessionId, payload는 필수입니다.");
		}

		PostCookReviewEntity entity = new PostCookReviewEntity(
				userId,
				payload.recipeId(),
				clientSessionId,
				payload.cookedAt(),
				payload.sourcePersonalVersionId(),
				payload.targetServings(),
				null,
				null,
				null);
		entity.cookingResultSchemaVersion =
				CookingResultFingerprint.SCHEMA_VERSION;
		entity.cookingResultPayload = payload;
		entity.cookingResultFingerprint =
				CookingResultFingerprint.sha256(payload);
		entity.reviewStatus = ReviewLifecycleStatus.PENDING_REVIEW;
		return entity;
	}

	/** 테스트·기존 POST 내부 호출 호환용. */
	public PostCookReviewEntity(UUID userId, UUID recipeId, Integer rating,
			String comment, String nextTimeNote) {
		this(userId, recipeId, null, Instant.now(), null, null, rating, comment, nextTimeNote);
	}
}
