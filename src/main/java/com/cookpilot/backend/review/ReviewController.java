package com.cookpilot.backend.review;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

	private final ReviewService reviewService;

	public ReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	/** 프론트가 조리를 마친 뒤 결과를 넘긴다. 서버에 세션이 없으므로 recipeId를 body로 받는다. */
	@PostMapping("/reviews")
	@ResponseStatus(HttpStatus.CREATED)
	public PostCookReview submit(@RequestBody SubmitReviewRequest request) {
		if (request.recipeId() == null) {
			throw new IllegalArgumentException("recipeId는 필수입니다.");
		}
		if (request.rating() == null) {
			throw new IllegalArgumentException("rating은 필수입니다.");
		}
		return reviewService.submit(request.recipeId(), request.rating(), request.comment(),
				request.nextTimeNote());
	}

	@GetMapping("/reviews/{reviewId}")
	public PostCookReview get(@PathVariable UUID reviewId) {
		return reviewService.findById(reviewId);
	}

	@GetMapping("/recipes/{recipeId}/reviews")
	public List<PostCookReview> listByRecipe(@PathVariable UUID recipeId) {
		return reviewService.findByRecipe(recipeId);
	}

	public record SubmitReviewRequest(UUID recipeId, Integer rating, String comment, String nextTimeNote) {
	}
}
