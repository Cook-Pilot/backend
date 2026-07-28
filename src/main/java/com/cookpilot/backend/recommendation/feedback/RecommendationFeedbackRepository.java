package com.cookpilot.backend.recommendation.feedback;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationFeedbackRepository
		extends JpaRepository<RecommendationFeedbackEntity, UUID> {

	Optional<RecommendationFeedbackEntity> findByUserIdAndRecommendationId(
			UUID userId, UUID recommendationId);

	List<RecommendationFeedbackEntity> findByUserIdAndRecipeIdOrderByCreatedAtDesc(
			UUID userId, UUID recipeId);
}
