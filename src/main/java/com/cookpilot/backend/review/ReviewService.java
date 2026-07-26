package com.cookpilot.backend.review;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.personalrecipe.PersonalRecipeService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersion;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionEntity;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionRepository;
import com.cookpilot.backend.recipe.RecipeEntity;
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
	private final PersonalRecipeVersionRepository personalRecipeVersionRepository;
	private final UserService userService;

	public ReviewService(PostCookReviewRepository reviewRepository, RecipeRepository recipeRepository,
			PersonalRecipeService personalRecipeService,
			PersonalRecipeVersionRepository personalRecipeVersionRepository,
			UserService userService) {
		this.reviewRepository = reviewRepository;
		this.recipeRepository = recipeRepository;
		this.personalRecipeService = personalRecipeService;
		this.personalRecipeVersionRepository = personalRecipeVersionRepository;
		this.userService = userService;
	}

	@Transactional
	public PostCookReview submit(SubmitReviewRequest request) {
		if (request.rating() < 1 || request.rating() > 5) {
			throw new IllegalArgumentException("rating은 1~5 사이여야 합니다.");
		}
		if (!recipeRepository.existsById(request.recipeId())) {
			throw new NotFoundException("레시피를 찾을 수 없습니다: " + request.recipeId());
		}

		UUID userId = userService.getCurrentUser().id();
		PostCookReviewEntity existing = reviewRepository
				.findByUserIdAndClientSessionId(userId, request.clientSessionId())
				.orElse(null);
		if (existing != null) {
			UUID versionId = personalRecipeService.findCreatedVersionId(existing.getId()).orElse(null);
			return toDto(existing, versionId);
		}

		// FK 순서: 버전이 source_review_id 로 리뷰를 가리키므로 리뷰를 먼저 저장한다.
		PostCookReviewEntity review = reviewRepository.save(new PostCookReviewEntity(
				userId,
				request.recipeId(),
				request.clientSessionId(),
				request.cookedAt() != null ? request.cookedAt() : Instant.now(),
				request.sourcePersonalVersionId(),
				request.targetServings(),
				request.rating(),
				request.comment(),
				request.nextTimeNote()));
		PersonalRecipeVersion version = personalRecipeService.createFromExecution(
				request.recipeId(), review.getId(), request.toExecutedRecipe()).orElse(null);

		return toDto(review, version != null ? version.id() : null);
	}

	@Transactional(readOnly = true)
	public PostCookReview findById(UUID reviewId) {
		UUID userId = userService.getCurrentUser().id();
		PostCookReviewEntity review = reviewRepository.findByIdAndUserId(reviewId, userId)
				.orElseThrow(() -> new NotFoundException("피드백을 찾을 수 없습니다: " + reviewId));
		return toDto(review, null);
	}

	@Transactional(readOnly = true)
	public List<PostCookReview> findByRecipe(UUID recipeId) {
		UUID userId = userService.getCurrentUser().id();
		return reviewRepository.findByUserIdAndRecipeIdOrderByCreatedAtDesc(userId, recipeId)
				.stream().map(r -> toDto(r, null)).toList();
	}

	@Transactional(readOnly = true)
	public List<CookingHistoryItem> findHistory(Instant from, Instant to) {
		if (from == null || to == null || !from.isBefore(to)) {
			throw new IllegalArgumentException("조회 시작 시각은 종료 시각보다 빨라야 합니다.");
		}
		UUID userId = userService.getCurrentUser().id();
		List<PostCookReviewEntity> reviews = reviewRepository
				.findByUserIdAndCookedAtGreaterThanEqualAndCookedAtLessThanOrderByCookedAtDesc(
						userId, from, to);
		if (reviews.isEmpty()) {
			return List.of();
		}

		Set<UUID> recipeIds = new HashSet<>();
		List<UUID> reviewIds = reviews.stream().map(review -> {
			recipeIds.add(review.getRecipeId());
			return review.getId();
		}).toList();
		Map<UUID, RecipeEntity> recipesById = new HashMap<>();
		recipeRepository.findAllById(recipeIds)
				.forEach(recipe -> recipesById.put(recipe.getId(), recipe));
		Map<UUID, PersonalRecipeVersionEntity> versionsByReviewId = new HashMap<>();
		personalRecipeVersionRepository.findBySourceReviewIdIn(reviewIds)
				.forEach(version -> versionsByReviewId.put(version.getSourceReviewId(), version));

		return reviews.stream().map(review -> {
			RecipeEntity recipe = recipesById.get(review.getRecipeId());
			PersonalRecipeVersionEntity version = versionsByReviewId.get(review.getId());
			return new CookingHistoryItem(
					review.getId(),
					review.getRecipeId(),
					recipe != null ? recipe.getTitle() : "삭제된 레시피",
					recipe != null ? recipe.getImageUrl() : null,
					review.getCookedAt(),
					review.getRating(),
					review.getComment(),
					review.getNextTimeNote(),
					review.getSourcePersonalVersionId(),
					version != null ? version.getId() : null,
					version != null ? version.getVersionNumber() : null,
					version != null ? version.getSummary() : null);
		}).toList();
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
				entity.getClientSessionId(),
				entity.getCookedAt(),
				entity.getSourcePersonalVersionId(),
				entity.getTargetServings(),
				entity.getRating(),
				entity.getComment(),
				entity.getNextTimeNote(),
				createdPersonalVersionId,
				entity.getCreatedAt()
		);
	}
}
