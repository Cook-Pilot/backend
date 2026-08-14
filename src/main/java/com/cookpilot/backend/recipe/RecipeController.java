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

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {

	private final RecipeService recipeService;
	private final PersonalRecipeService personalRecipeService;
	private final FavoriteService favoriteService;

	public RecipeController(RecipeService recipeService,
			PersonalRecipeService personalRecipeService,
			FavoriteService favoriteService) {
		this.recipeService = recipeService;
		this.personalRecipeService = personalRecipeService;
		this.favoriteService = favoriteService;
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
		Map<UUID, PersonalRecipeVersion> latestByRecipe = personalRecipeService.findLatestByRecipes(
				recipes.stream().map(RecipeOverview::id).toList());
		Set<UUID> favoriteRecipeIds = favoriteService.findFavoriteRecipeIds(
				recipes.stream().map(RecipeOverview::id).toList());
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
