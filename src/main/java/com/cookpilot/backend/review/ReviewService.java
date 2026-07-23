package com.cookpilot.backend.review;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.personalrecipe.PersonalRecipeService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersion;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.user.UserService;

/**
 * 조리 후 피드백(JPA 영속). 저장 시 개인 레시피 버전을 함께 생성하고,
 * 버전의 source_review_id 가 이 리뷰를 가리킨다(FK — 리뷰를 먼저 저장해야 한다).
 *
 * 조리 진행(단계 이동/타이머)은 프론트가 로컬에서 관리하므로 서버는 세션을 모른다.
 * 프론트가 조리를 마친 뒤 recipeId와 함께 결과를 넘기면 그것이 조리 1회의 기록이 된다.
 */
@Service
public class ReviewService {

	private final PostCookReviewRepository reviewRepository;
	private final RecipeRepository recipeRepository;
	private final PersonalRecipeService personalRecipeService;
	private final UserService userService;

	public ReviewService(PostCookReviewRepository reviewRepository, RecipeRepository recipeRepository,
			PersonalRecipeService personalRecipeService, UserService userService) {
		this.reviewRepository = reviewRepository;
		this.recipeRepository = recipeRepository;
		this.personalRecipeService = personalRecipeService;
		this.userService = userService;
	}

	@Transactional
	public PostCookReview submit(UUID recipeId, int rating, String comment, String nextTimeNote) {
		if (rating < 1 || rating > 5) {
			throw new IllegalArgumentException("rating은 1~5 사이여야 합니다.");
		}
		if (!recipeRepository.existsById(recipeId)) {
			throw new NotFoundException("레시피를 찾을 수 없습니다: " + recipeId);
		}

		// FK 순서: 버전이 source_review_id 로 리뷰를 가리키므로 리뷰를 먼저 저장한다.
		PostCookReviewEntity review = reviewRepository.save(new PostCookReviewEntity(
				userService.getCurrentUser().id(), recipeId, rating, comment, nextTimeNote));
		PersonalRecipeVersion version = personalRecipeService.createFromReview(
				recipeId, review.getId(), comment, nextTimeNote);

		return toDto(review, version.id());
	}

	@Transactional(readOnly = true)
	public PostCookReview findById(UUID reviewId) {
		PostCookReviewEntity review = reviewRepository.findById(reviewId)
				.orElseThrow(() -> new NotFoundException("피드백을 찾을 수 없습니다: " + reviewId));
		return toDto(review, null);
	}

	@Transactional(readOnly = true)
	public List<PostCookReview> findByRecipe(UUID recipeId) {
		return reviewRepository.findByRecipeIdOrderByCreatedAtDesc(recipeId)
				.stream().map(r -> toDto(r, null)).toList();
	}

	/**
	 * createdPersonalVersionId 는 생성 응답에서만 채운다(조회 시점엔 버전이 리뷰를 역참조하므로
	 * source_review_id 로 추적 가능).
	 */
	private PostCookReview toDto(PostCookReviewEntity entity, UUID createdPersonalVersionId) {
		return new PostCookReview(
				entity.getId(),
				entity.getUserId(),
				entity.getRecipeId(),
				entity.getRating(),
				entity.getComment(),
				entity.getNextTimeNote(),
				createdPersonalVersionId,
				entity.getCreatedAt()
		);
	}
}
