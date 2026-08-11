package com.cookpilot.backend.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

	private final ReviewService reviewService;
	private final ReviewPhotoService reviewPhotoService;

	public ReviewController(ReviewService reviewService, ReviewPhotoService reviewPhotoService) {
		this.reviewService = reviewService;
		this.reviewPhotoService = reviewPhotoService;
	}

	/** 프론트가 조리를 마친 뒤 결과를 넘긴다. 서버에 세션이 없으므로 recipeId를 body로 받는다. */
	@PostMapping("/reviews")
	@ResponseStatus(HttpStatus.CREATED)
	public PostCookReview submit(@Valid @RequestBody SubmitReviewRequest request) {
		return reviewService.submit(request);
	}

	/** 리뷰 사진 1장 업로드. 여러 장은 장수만큼 반복 호출한다. 상세는 {@link ReviewPhotoService}. */
	@PostMapping(value = "/reviews/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public PhotoUploadResponse uploadPhoto(@RequestPart("file") MultipartFile file) {
		return new PhotoUploadResponse(reviewPhotoService.upload(file));
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
