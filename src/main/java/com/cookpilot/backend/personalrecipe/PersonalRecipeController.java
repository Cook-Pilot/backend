package com.cookpilot.backend.personalrecipe;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PersonalRecipeController {

	private final PersonalRecipeService personalRecipeService;

	public PersonalRecipeController(PersonalRecipeService personalRecipeService) {
		this.personalRecipeService = personalRecipeService;
	}

	@GetMapping("/recipes/{recipeId}/personal-versions")
	public List<PersonalRecipeVersion> listByRecipe(@PathVariable UUID recipeId) {
		return personalRecipeService.findByRecipe(recipeId);
	}

	@GetMapping("/personal-versions/{versionId}")
	public PersonalRecipeVersion get(@PathVariable UUID versionId) {
		return personalRecipeService.findById(versionId);
	}
}
