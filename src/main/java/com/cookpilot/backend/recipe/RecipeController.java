package com.cookpilot.backend.recipe;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cookpilot.backend.favorite.FavoriteService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersion;
import com.cookpilot.backend.user.UserService;

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {

	private final RecipeService recipeService;
	private final PersonalRecipeService personalRecipeService;
	private final FavoriteService favoriteService;
	private final UserService userService;

	public RecipeController(RecipeService recipeService,
			PersonalRecipeService personalRecipeService,
			FavoriteService favoriteService,
			UserService userService) {
		this.recipeService = recipeService;
		this.personalRecipeService = personalRecipeService;
		this.favoriteService = favoriteService;
		this.userService = userService;
	}

	@GetMapping
	public List<RecipeSummaryResponse> list() {
		List<RecipeOverview> recipes = recipeService.findAll();
		return summarize(recipes);
	}

	@GetMapping("/search")
	public RecipeSearchResponse search(
			@RequestParam(defaultValue = "") String title,
			@RequestParam(defaultValue = "") String ingredient,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "9") int size) {
		Page<RecipeOverview> result = recipeService.search(title, ingredient, page, size);
		return new RecipeSearchResponse(
				summarize(result.getContent()),
				result.getNumber() + 1,
				result.getSize(),
				result.getTotalPages(),
				result.getTotalElements());
	}

	private List<RecipeSummaryResponse> summarize(List<RecipeOverview> recipes) {
		if (recipes.isEmpty()) {
			return List.of();
		}
		// 목록·검색·상세는 게스트에게도 열려 있다 — 로그인 요구는 앱이 저장 시점에 한다.
		// 세션이 없으면 개인화(내 버전·즐겨찾기)만 기본값으로 비운다.
		boolean loggedIn = userService.currentUserIdIfPresent().isPresent();
		List<UUID> recipeIds = recipes.stream().map(RecipeOverview::id).toList();
		Map<UUID, PersonalRecipeVersion> latestByRecipe = loggedIn
				? personalRecipeService.findLatestByRecipes(recipeIds)
				: Map.of();
		Set<UUID> favoriteRecipeIds = loggedIn
				? favoriteService.findFavoriteRecipeIds(recipeIds)
				: Set.of();
		return recipes.stream()
				.map(recipe -> {
					PersonalRecipeVersion latest = latestByRecipe.get(recipe.id());
					return new RecipeSummaryResponse(
							recipe.id(),
							recipe.title(),
							recipe.description(),
							recipe.imageUrl(),
							latest != null,
							latest == null ? null : latest.id(),
							favoriteRecipeIds.contains(recipe.id())
					);
				})
				.toList();
	}

	public record RecipeSearchResponse(
			List<RecipeSummaryResponse> items,
			int page,
			int pageSize,
			int totalPages,
			long totalItems) {
	}

	@GetMapping("/{recipeId}")
	public Recipe get(@PathVariable UUID recipeId) {
		return recipeService.findById(recipeId);
	}
}
