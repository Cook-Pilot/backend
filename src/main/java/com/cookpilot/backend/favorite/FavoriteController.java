package com.cookpilot.backend.favorite;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FavoriteController {

	private final FavoriteService favoriteService;

	public FavoriteController(FavoriteService favoriteService) {
		this.favoriteService = favoriteService;
	}

	@GetMapping("/api/v1/favorites")
	public List<FavoriteRecipeResponse> list() {
		return favoriteService.findAll();
	}

	@PutMapping("/api/v1/recipes/{recipeId}/favorite")
	public FavoriteRecipeResponse add(@PathVariable UUID recipeId) {
		return favoriteService.add(recipeId);
	}

	@DeleteMapping("/api/v1/recipes/{recipeId}/favorite")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@PathVariable UUID recipeId) {
		favoriteService.remove(recipeId);
	}
}
