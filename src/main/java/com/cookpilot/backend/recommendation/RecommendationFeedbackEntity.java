package com.cookpilot.backend.recommendation;

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

@Entity
@Table(name = "recommendation_feedback")
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

	public RecommendationFeedbackEntity(
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

	public UUID getId() {
		return id;
	}

	public UUID getRecommendationId() {
		return recommendationId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getRecipeId() {
		return recipeId;
	}

	public UUID getOriginalIngredientId() {
		return originalIngredientId;
	}

	public RecommendationDecision getDecision() {
		return decision;
	}

	public BigDecimal getOriginalAmount() {
		return originalAmount;
	}

	public BigDecimal getSuggestedAmount() {
		return suggestedAmount;
	}

	public BigDecimal getAppliedAmount() {
		return appliedAmount;
	}

	public String getUnit() {
		return unit;
	}

	public String getReason() {
		return reason;
	}

	public String getExplanationSource() {
		return explanationSource;
	}

	public String getModel() {
		return model;
	}

	public String getPromptVersion() {
		return promptVersion;
	}

	public List<UUID> getEvidenceReviewIds() {
		return List.copyOf(evidenceReviewIds);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
