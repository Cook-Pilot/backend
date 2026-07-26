package com.cookpilot.backend.pantry;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pantry")
public class PantryController {

	private final PantryService pantryService;

	public PantryController(PantryService pantryService) {
		this.pantryService = pantryService;
	}

	@GetMapping("/ingredient-catalog")
	public List<PantryIngredientCatalogItemResponse> catalog() {
		return pantryService.listCatalog();
	}

	@GetMapping("/items")
	public List<PantryItemResponse> items() {
		return pantryService.listItems();
	}

	@PostMapping("/items")
	@ResponseStatus(HttpStatus.CREATED)
	public PantryItemResponse addItem(@RequestBody AddPantryItemRequest request) {
		return pantryService.addItem(request);
	}

	@DeleteMapping("/items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeItem(@PathVariable UUID itemId) {
		pantryService.removeItem(itemId);
	}

	@GetMapping("/recipe-suggestions")
	public PantryRecipeSuggestionsResponse suggestions() {
		return pantryService.suggestRecipes();
	}
}
