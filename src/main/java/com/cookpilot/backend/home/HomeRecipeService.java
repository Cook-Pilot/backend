package com.cookpilot.backend.home;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.personalrecipe.PersonalRecipeService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersion;
import com.cookpilot.backend.recipe.RecipeEntity;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.review.PostCookReviewEntity;
import com.cookpilot.backend.review.PostCookReviewRepository;
import com.cookpilot.backend.user.UserService;

@Service
@Transactional(readOnly = true)
public class HomeRecipeService {

	private static final int RECENT_RECIPE_LIMIT = 10;

	private final PostCookReviewRepository postCookReviewRepository;
	private final RecipeRepository recipeRepository;
	private final PersonalRecipeService personalRecipeService;
	private final UserService userService;

	public HomeRecipeService(PostCookReviewRepository postCookReviewRepository,
			RecipeRepository recipeRepository,
			PersonalRecipeService personalRecipeService,
			UserService userService) {
		this.postCookReviewRepository = postCookReviewRepository;
		this.recipeRepository = recipeRepository;
		this.personalRecipeService = personalRecipeService;
		this.userService = userService;
	}

	public List<RecentRecipeResponse> findRecentRecipes() {
		UUID userId = userService.getCurrentUser().id();
		List<PostCookReviewEntity> latestReviews =
				postCookReviewRepository.findRecentDistinctActiveByUserId(
						userId, RECENT_RECIPE_LIMIT);
		if (latestReviews.isEmpty()) {
			return List.of();
		}

		List<UUID> recipeIds = latestReviews.stream()
				.map(PostCookReviewEntity::getRecipeId)
				.toList();
		Map<UUID, RecipeEntity> recipesById = recipeRepository.findAllById(recipeIds).stream()
				.collect(Collectors.toMap(RecipeEntity::getId, recipe -> recipe));
		Map<UUID, PersonalRecipeVersion> latestVersionByRecipe =
				personalRecipeService.findLatestByRecipes(recipeIds);

		return latestReviews.stream()
				.filter(review -> {
					RecipeEntity recipe = recipesById.get(review.getRecipeId());
					return recipe != null && "active".equals(recipe.getStatus());
				})
				.map(review -> {
					RecipeEntity recipe = recipesById.get(review.getRecipeId());
					PersonalRecipeVersion latestVersion =
							latestVersionByRecipe.get(review.getRecipeId());
					return new RecentRecipeResponse(
							recipe.getId(),
							recipe.getTitle(),
							recipe.getDescription(),
							recipe.getImageUrl(),
							review.getCreatedAt(),
							review.getRating(),
							latestVersion != null,
							latestVersion == null ? null : latestVersion.id());
				})
				.toList();
	}
}
