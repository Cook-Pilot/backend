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

	/** 상세: 메타 + 합성된 재료/단계(그대로 렌더링 가능) + 원시 diff(무엇이 바뀌었는지). */
	@GetMapping("/personal-versions/{versionId}")
	public PersonalRecipeVersionDetail get(@PathVariable UUID versionId) {
		return personalRecipeService.findDetailById(versionId);
	}
}
