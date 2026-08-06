package com.cookpilot.backend.recommendation.feedback;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitRecommendationFeedbackRequest(
		@NotNull(message = "originalIngredientId는 필수입니다.") UUID originalIngredientId,
		@NotNull(message = "decision은 필수입니다.") RecommendationDecision decision,
		@NotNull(message = "originalAmount는 필수입니다.") BigDecimal originalAmount,
		@NotNull(message = "suggestedAmount는 필수입니다.") BigDecimal suggestedAmount,
		BigDecimal appliedAmount,
		String unit,
		String reason,
		@NotNull(message = "explanationSource는 필수입니다.") String explanationSource,
		String model,
		@NotBlank(message = "promptVersion은 필수입니다.") String promptVersion,
		List<UUID> evidenceReviewIds
) {
	public SubmitRecommendationFeedbackRequest {
		evidenceReviewIds = evidenceReviewIds == null
				? List.of()
				: List.copyOf(evidenceReviewIds);
	}
}
