package com.cookpilot.backend.recommendation;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.cookpilot.backend.recommendation.feedback.RecommendationFeedbackResponse;
import com.cookpilot.backend.recommendation.feedback.RecommendationFeedbackService;
import com.cookpilot.backend.recommendation.feedback.SubmitRecommendationFeedbackRequest;

@RestController
@RequestMapping("/api/v1/recipes/{recipeId}")
public class RecommendationController {

	private final RecommendationService recommendationService;
	private final RecommendationFeedbackService feedbackService;

	public RecommendationController(
			RecommendationService recommendationService,
			RecommendationFeedbackService feedbackService) {
		this.recommendationService = recommendationService;
		this.feedbackService = feedbackService;
	}

	@GetMapping("/next-cook-recommendations")
	public NextCookRecommendationResponse recommend(@PathVariable UUID recipeId) {
		return recommendationService.recommend(recipeId);
	}

	@PutMapping("/recommendation-feedback/{recommendationId}")
	public RecommendationFeedbackResponse feedback(
			@PathVariable UUID recipeId,
			@PathVariable UUID recommendationId,
			@Valid @RequestBody SubmitRecommendationFeedbackRequest request) {
		return feedbackService.recordFeedback(recipeId, recommendationId, request);
	}
}
