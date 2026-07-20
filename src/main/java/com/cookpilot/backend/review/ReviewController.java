package com.cookpilot.backend.review;

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
@RequestMapping("/api/v1/cook-sessions/{sessionId}/review")
public class ReviewController {

	private final ReviewService reviewService;

	public ReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PostCookReview submit(@PathVariable UUID sessionId, @RequestBody SubmitReviewRequest request) {
		if (request.rating() == null) {
			throw new IllegalArgumentException("rating은 필수입니다.");
		}
		return reviewService.submit(sessionId, request.rating(), request.comment(), request.nextTimeNote());
	}

	@GetMapping
	public PostCookReview get(@PathVariable UUID sessionId) {
		return reviewService.findBySessionId(sessionId);
	}

	public record SubmitReviewRequest(Integer rating, String comment, String nextTimeNote) {
	}
}
