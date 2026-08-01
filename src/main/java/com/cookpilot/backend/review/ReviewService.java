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
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionEntity;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionRepository;
import com.cookpilot.backend.recipe.RecipeEntity;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.user.UserService;

/**
 * 조리 후 피드백(JPA 영속). 리뷰 저장과 개인 레시피 버전 생성은 분리돼 있다 —
 * 이 서비스는 리뷰만 쓰고, 버전은 수정 파이프라인이 나중에 만들면서
 * source_review_id 로 이 리뷰를 역참조한다(FK — 리뷰가 먼저 있어야 한다).
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
		// 인분은 여기서 막아야 한다. 리뷰가 먼저 저장되고 수정 파이프라인이 이 값을 읽으므로,
		// 통과시키면 리뷰만 남고 버전 생성이 400 나서 쓸 수 없는 조리 기록이 쌓인다.
		if (request.targetServings() != null && request.targetServings().signum() <= 0) {
			throw new IllegalArgumentException("targetServings는 0보다 커야 합니다.");
		}

		// 같은 사용자의 동시 재전송을 직렬화해 멱등성 조회와 저장을 하나의 원자적 흐름으로 만든다.
		UUID userId = userService.lockCurrentUser().id();
		// 조리에 쓴 개인 버전은 새 버전의 계보 부모가 된다 — 남의 버전이 박히지 않게 여기서 대조한다.
		personalRecipeService.validateSourceVersion(
				userId, request.recipeId(), request.sourcePersonalVersionId());
		PostCookReviewEntity existing = reviewRepository
				.findByUserIdAndClientSessionId(userId, request.clientSessionId())
				.orElse(null);
		if (existing != null) {
			// 재시도. 그 사이 수정 파이프라인이 버전을 만들었을 수 있으므로 역참조로 확인한다.
			UUID versionId = personalRecipeService.findCreatedVersionId(existing.getId()).orElse(null);
			return PostCookReview.from(existing, versionId);
		}

		PostCookReviewEntity review = reviewRepository.save(PostCookReviewEntity.of(userId, request));

		// 개인 버전은 여기서 만들지 않는다. 리뷰는 조리 기록일 뿐이고,
		// 버전 생성은 수정 파이프라인(setup/cooking/review 층)이 소유한다.
		return PostCookReview.from(review, null);
	}

	@Transactional(readOnly = true)
	public PostCookReview findById(UUID reviewId) {
		UUID userId = userService.getCurrentUser().id();
		PostCookReviewEntity review = reviewRepository.findByIdAndUserId(reviewId, userId)
				.orElseThrow(() -> new NotFoundException("피드백을 찾을 수 없습니다: " + reviewId));
		return PostCookReview.from(review, personalRecipeService.findCreatedVersionId(reviewId).orElse(null));
	}

	@Transactional(readOnly = true)
	public List<PostCookReview> findByRecipe(UUID recipeId) {
		UUID userId = userService.getCurrentUser().id();
		List<PostCookReviewEntity> reviews = reviewRepository
				.findByUserIdAndRecipeIdOrderByCreatedAtDesc(userId, recipeId);
		Map<UUID, UUID> versionIdByReviewId = findVersionIdsByReviewIds(
				reviews.stream().map(PostCookReviewEntity::getId).toList());
		return reviews.stream()
				.map(review -> PostCookReview.from(review, versionIdByReviewId.get(review.getId())))
				.toList();
	}

	/** 리뷰 → 그 리뷰에서 파생된 개인 버전 id. 버전이 리뷰를 역참조하므로 한 번에 모아 읽는다. */
	private Map<UUID, UUID> findVersionIdsByReviewIds(List<UUID> reviewIds) {
		if (reviewIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, UUID> versionIdByReviewId = new HashMap<>();
		personalRecipeVersionRepository.findBySourceReviewIdIn(reviewIds)
				.forEach(version -> versionIdByReviewId.put(version.getSourceReviewId(), version.getId()));
		return versionIdByReviewId;
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

}
