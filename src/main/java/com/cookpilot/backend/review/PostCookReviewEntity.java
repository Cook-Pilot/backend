package com.cookpilot.backend.review;

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

/**
 * post_cook_reviews 테이블 매핑(그룹 A 구조).
 *
 * rating/comment/next_time_note 는 사용자 입력분(그룹 A).
 * structured_feedback(JSONB)는 리뷰를 AI가 구조화한 산출물이라 내부 구조는
 * AI 파트 확정 대상(그룹 B). 여기서는 opaque Map 으로만 매핑한다.
 */
@Entity
@Table(name = "post_cook_reviews")
public class PostCookReviewEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "cook_session_id", nullable = false)
	private UUID cookSessionId;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	// DB의 CHECK (rating BETWEEN 1 AND 5)와 짝을 이룬다. null(무평점)은 허용, 값이 있으면 1~5.
	@Min(1)
	@Max(5)
	@Column(name = "rating")
	private Integer rating;

	@Column(name = "comment")
	private String comment;

	@Column(name = "next_time_note")
	private String nextTimeNote;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "structured_feedback", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> structuredFeedback = new HashMap<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PostCookReviewEntity() {
	}

	public PostCookReviewEntity(UUID cookSessionId, UUID userId, UUID recipeId, Integer rating,
			String comment, String nextTimeNote) {
		this.cookSessionId = cookSessionId;
		this.userId = userId;
		this.recipeId = recipeId;
		this.rating = rating;
		this.comment = comment;
		this.nextTimeNote = nextTimeNote;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCookSessionId() {
		return cookSessionId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getRecipeId() {
		return recipeId;
	}

	public Integer getRating() {
		return rating;
	}

	public String getComment() {
		return comment;
	}

	public String getNextTimeNote() {
		return nextTimeNote;
	}

	public Map<String, Object> getStructuredFeedback() {
		return structuredFeedback;
	}

	public void setStructuredFeedback(Map<String, Object> structuredFeedback) {
		this.structuredFeedback = structuredFeedback;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
