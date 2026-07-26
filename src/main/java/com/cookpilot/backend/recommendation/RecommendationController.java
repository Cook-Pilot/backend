package com.cookpilot.backend.recommendation;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recipes/{recipeId}")
public class RecommendationController {

	private final RecommendationService recommendationService;

	public RecommendationController(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@GetMapping("/next-cook-recommendations")
	public NextCookRecommendationResponse recommend(@PathVariable UUID recipeId) {
		return recommendationService.recommend(recipeId);
	}

	@PutMapping("/recommendation-feedback/{recommendationId}")
	public RecommendationFeedbackResponse feedback(
			@PathVariable UUID recipeId,
			@PathVariable UUID recommendationId,
			@RequestBody SubmitRecommendationFeedbackRequest request) {
		return recommendationService.recordFeedback(recipeId, recommendationId, request);
	}
}
