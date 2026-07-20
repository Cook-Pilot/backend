package com.cookpilot.backend.recipe;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cookpilot.backend.personalrecipe.PersonalRecipeService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersion;

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {

	private final RecipeService recipeService;
	private final PersonalRecipeService personalRecipeService;

	public RecipeController(RecipeService recipeService, PersonalRecipeService personalRecipeService) {
		this.recipeService = recipeService;
		this.personalRecipeService = personalRecipeService;
	}

	@GetMapping
	public List<RecipeSummaryResponse> list() {
		return recipeService.findAll().stream()
				.map(recipe -> {
					PersonalRecipeVersion latest = personalRecipeService
							.findLatestByRecipe(recipe.id())
							.orElse(null);
					return new RecipeSummaryResponse(
							recipe.id(),
							recipe.title(),
							recipe.description(),
							latest != null,
							latest == null ? null : latest.id()
					);
				})
				.toList();
	}

	@GetMapping("/{recipeId}")
	public Recipe get(@PathVariable UUID recipeId) {
		return recipeService.findById(recipeId);
	}
}
