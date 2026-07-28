package com.cookpilot.backend.recommendation.feedback;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import lombok.Builder;
import lombok.Getter;

@Entity
@Table(name = "recommendation_feedback")
@Getter
public class RecommendationFeedbackEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "recommendation_id", nullable = false)
	private UUID recommendationId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	@Column(name = "original_ingredient_id")
	private UUID originalIngredientId;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision", nullable = false)
	private RecommendationDecision decision;

	@Column(name = "original_amount")
	private BigDecimal originalAmount;

	@Column(name = "suggested_amount")
	private BigDecimal suggestedAmount;

	@Column(name = "applied_amount")
	private BigDecimal appliedAmount;

	@Column(name = "unit")
	private String unit;

	@Column(name = "reason")
	private String reason;

	@Column(name = "explanation_source", nullable = false)
	private String explanationSource;

	@Column(name = "model")
	private String model;

	@Column(name = "prompt_version", nullable = false)
	private String promptVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "evidence_review_ids", nullable = false, columnDefinition = "jsonb")
	private List<UUID> evidenceReviewIds = new ArrayList<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RecommendationFeedbackEntity() {
	}

	@Builder
	private RecommendationFeedbackEntity(
			UUID recommendationId,
			UUID userId,
			UUID recipeId,
			UUID originalIngredientId,
			RecommendationDecision decision,
			BigDecimal originalAmount,
			BigDecimal suggestedAmount,
			BigDecimal appliedAmount,
			String unit,
			String reason,
			String explanationSource,
			String model,
			String promptVersion,
			List<UUID> evidenceReviewIds) {
		this.recommendationId = recommendationId;
		this.userId = userId;
		this.recipeId = recipeId;
		this.originalIngredientId = originalIngredientId;
		this.decision = decision;
		this.originalAmount = originalAmount;
		this.suggestedAmount = suggestedAmount;
		this.appliedAmount = appliedAmount;
		this.unit = unit;
		this.reason = reason;
		this.explanationSource = explanationSource;
		this.model = model;
		this.promptVersion = promptVersion;
		this.evidenceReviewIds = new ArrayList<>(evidenceReviewIds);
	}

	// 내부 리스트를 그대로 넘기지 않도록 방어 복사. Lombok 은 직접 정의한 게터를 덮어쓰지 않는다.
	public List<UUID> getEvidenceReviewIds() {
		return List.copyOf(evidenceReviewIds);
	}
}
