package com.cookpilot.backend.recipe;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

	@GetMapping("/{recipeId}")
	public Recipe get(@PathVariable UUID recipeId) {
		return recipeService.findById(recipeId);
	}
}
