package com.cookpilot.backend.review;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.personalrecipe.PersonalRecipeService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersion;
import com.cookpilot.backend.recipe.RecipeService;
import com.cookpilot.backend.user.UserService;

/**
 * repository 계층 미확정. 조리 후 피드백을 인메모리로 저장하고,
 * 저장 시 개인 레시피 버전을 생성한다(조정값 구조화는 AI 미확정 - 목데이터).
 *
 * 조리 진행(단계 이동/타이머)은 프론트가 로컬에서 관리하므로 서버는 세션을 모른다.
 * 프론트가 조리를 마친 뒤 recipeId와 함께 결과를 넘기면 그것이 조리 1회의 기록이 된다.
 */
@Service
public class ReviewService {

	private final Map<UUID, PostCookReview> reviewsById = new ConcurrentHashMap<>();
	private final RecipeService recipeService;
	private final PersonalRecipeService personalRecipeService;
	private final UserService userService;

	public ReviewService(RecipeService recipeService, PersonalRecipeService personalRecipeService,
			UserService userService) {
		this.recipeService = recipeService;
		this.personalRecipeService = personalRecipeService;
		this.userService = userService;
	}

	public PostCookReview submit(UUID recipeId, int rating, String comment, String nextTimeNote) {
		if (rating < 1 || rating > 5) {
			throw new IllegalArgumentException("rating은 1~5 사이여야 합니다.");
		}
		recipeService.findById(recipeId); // 없는 레시피면 404

		UUID reviewId = UUID.randomUUID();
		PersonalRecipeVersion version = personalRecipeService.createFromReview(
				recipeId, reviewId, comment, nextTimeNote);

		PostCookReview review = new PostCookReview(
				reviewId,
				userService.getCurrentUser().id(),
				recipeId,
				rating,
				comment,
				nextTimeNote,
				version.id(),
				Instant.now()
		);
		reviewsById.put(reviewId, review);
		return review;
	}

	public PostCookReview findById(UUID reviewId) {
		PostCookReview review = reviewsById.get(reviewId);
		if (review == null) {
			throw new NotFoundException("피드백을 찾을 수 없습니다: " + reviewId);
		}
		return review;
	}

	public List<PostCookReview> findByRecipe(UUID recipeId) {
		return reviewsById.values().stream()
				.filter(r -> r.recipeId().equals(recipeId))
				.sorted(Comparator.comparing(PostCookReview::createdAt).reversed())
				.toList();
	}
}
