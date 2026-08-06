package com.cookpilot.backend.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

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
	public PostCookReview submit(@Valid @RequestBody SubmitReviewRequest request) {
		return reviewService.submit(request);
	}

	@GetMapping("/reviews/{reviewId}")
	public PostCookReview get(@PathVariable UUID reviewId) {
		return reviewService.findById(reviewId);
	}

	@GetMapping("/recipes/{recipeId}/reviews")
	public List<PostCookReview> listByRecipe(@PathVariable UUID recipeId) {
		return reviewService.findByRecipe(recipeId);
	}

	@GetMapping("/cooking-history")
	public List<CookingHistoryItem> history(
			@RequestParam Instant from,
			@RequestParam Instant to) {
		return reviewService.findHistory(from, to);
	}

}
