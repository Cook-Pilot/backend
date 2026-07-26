package com.cookpilot.backend.pantry;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.recipe.RecipeEntity;
import com.cookpilot.backend.recipe.RecipeIngredientEntity;
import com.cookpilot.backend.recipe.RecipeIngredientRepository;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.user.UserService;

@Service
@Transactional(readOnly = true)
public class PantryService {

	private static final int MAX_SUGGESTIONS = 3;

	private final PantryIngredientCatalogRepository catalogRepository;
	private final PantryItemRepository itemRepository;
	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final UserService userService;

	public PantryService(
			PantryIngredientCatalogRepository catalogRepository,
			PantryItemRepository itemRepository,
			RecipeRepository recipeRepository,
			RecipeIngredientRepository recipeIngredientRepository,
			UserService userService) {
		this.catalogRepository = catalogRepository;
		this.itemRepository = itemRepository;
		this.recipeRepository = recipeRepository;
		this.recipeIngredientRepository = recipeIngredientRepository;
		this.userService = userService;
	}

	public List<PantryIngredientCatalogItemResponse> listCatalog() {
		return catalogRepository.findAllByOrderBySortOrderAsc().stream()
				.map(entity -> new PantryIngredientCatalogItemResponse(
						entity.getName(), entity.getEmoji(), entity.getDefaultShelfLifeDays()))
				.toList();
	}

	public List<PantryItemResponse> listItems() {
		UUID userId = userService.getCurrentUser().id();
		LocalDate today = LocalDate.now();
		Map<String, PantryIngredientCatalogEntity> catalogByName = catalogByName();
		return itemRepository.findByUserIdOrderByUseByDateAsc(userId).stream()
				.map(item -> toResponse(item, catalogByName, today))
				.toList();
	}

	@Transactional
	public PantryItemResponse addItem(AddPantryItemRequest request) {
		UUID userId = userService.getCurrentUser().id();
		String ingredientName = trimToNull(request.ingredientName());
		if (ingredientName == null) {
			throw new IllegalArgumentException("담을 재료를 선택해 주세요.");
		}
		PantryIngredientCatalogEntity catalogEntry = catalogRepository.findById(ingredientName)
				.orElseThrow(() -> new IllegalArgumentException(
						"카탈로그에 없는 재료입니다: " + ingredientName));
		LocalDate useByDate = request.useByDate() != null
				? request.useByDate()
				: LocalDate.now().plusDays(catalogEntry.getDefaultShelfLifeDays());

		PantryItemEntity item = itemRepository.findByUserIdAndIngredientName(userId, ingredientName)
				.orElse(null);
		if (item == null) {
			item = itemRepository.save(new PantryItemEntity(userId, ingredientName, useByDate));
		} else {
			item.setUseByDate(useByDate);
		}
		return toResponse(item, Map.of(ingredientName, catalogEntry), LocalDate.now());
	}

	@Transactional
	public void removeItem(UUID itemId) {
		UUID userId = userService.getCurrentUser().id();
		itemRepository.findByIdAndUserId(itemId, userId)
				.orElseThrow(() -> new NotFoundException("보유 재료를 찾을 수 없습니다: " + itemId));
		itemRepository.deleteByIdAndUserId(itemId, userId);
	}

	public PantryRecipeSuggestionsResponse suggestRecipes() {
		UUID userId = userService.getCurrentUser().id();
		LocalDate today = LocalDate.now();
		Map<String, PantryIngredientCatalogEntity> catalogByName = catalogByName();
		List<PantryItemEntity> items = itemRepository.findByUserIdOrderByUseByDateAsc(userId);
		if (items.isEmpty()) {
			return new PantryRecipeSuggestionsResponse(Instant.now(), List.of());
		}
		Map<String, PantryItemEntity> pantryByNormalizedName = items.stream()
				.collect(Collectors.toMap(
						item -> normalizeName(item.getIngredientName()),
						item -> item,
						(left, right) -> left));

		List<RecipeEntity> recipes = recipeRepository.findByStatusOrderByTitleAscIdAsc("active");
		List<UUID> recipeIds = recipes.stream().map(RecipeEntity::getId).toList();
		Map<UUID, List<RecipeIngredientEntity>> ingredientsByRecipe =
				recipeIngredientRepository.findByRecipeIdIn(recipeIds).stream()
						.collect(Collectors.groupingBy(RecipeIngredientEntity::getRecipeId));

		List<PantryRecipeSuggestion> suggestions = new ArrayList<>();
		for (RecipeEntity recipe : recipes) {
			List<RecipeIngredientEntity> ingredients =
					ingredientsByRecipe.getOrDefault(recipe.getId(), List.of());
			List<PantryMatchedIngredient> matched = matchIngredients(
					ingredients, pantryByNormalizedName, catalogByName, today);
			if (matched.isEmpty()) {
				continue;
			}
			int mostUrgent = matched.stream()
					.mapToInt(PantryMatchedIngredient::daysUntilExpiry)
					.min()
					.orElseThrow();
			suggestions.add(new PantryRecipeSuggestion(
					recipe.getId(), recipe.getTitle(), recipe.getImageUrl(), matched, mostUrgent));
		}

		suggestions.sort(Comparator
				.comparingInt(PantryRecipeSuggestion::mostUrgentDaysUntilExpiry)
				.thenComparing(Comparator.comparingInt(
						(PantryRecipeSuggestion suggestion) -> suggestion.matchedIngredients().size())
						.reversed())
				.thenComparing(PantryRecipeSuggestion::recipeTitle));

		return new PantryRecipeSuggestionsResponse(
				Instant.now(),
				suggestions.stream().limit(MAX_SUGGESTIONS).toList());
	}

	private List<PantryMatchedIngredient> matchIngredients(
			List<RecipeIngredientEntity> recipeIngredients,
			Map<String, PantryItemEntity> pantryByNormalizedName,
			Map<String, PantryIngredientCatalogEntity> catalogByName,
			LocalDate today) {
		List<PantryMatchedIngredient> matched = new ArrayList<>();
		for (RecipeIngredientEntity ingredient : recipeIngredients) {
			PantryItemEntity pantryItem =
					pantryByNormalizedName.get(normalizeName(ingredient.getName()));
			if (pantryItem == null) {
				continue;
			}
			boolean alreadyMatched = matched.stream()
					.anyMatch(existing -> existing.ingredientName().equals(pantryItem.getIngredientName()));
			if (alreadyMatched) {
				continue;
			}
			PantryIngredientCatalogEntity catalogEntry =
					catalogByName.get(pantryItem.getIngredientName());
			matched.add(new PantryMatchedIngredient(
					pantryItem.getIngredientName(),
					catalogEntry == null ? "" : catalogEntry.getEmoji(),
					daysUntilExpiry(pantryItem.getUseByDate(), today)));
		}
		return matched;
	}

	private Map<String, PantryIngredientCatalogEntity> catalogByName() {
		return catalogRepository.findAll().stream()
				.collect(Collectors.toMap(PantryIngredientCatalogEntity::getName, entity -> entity));
	}

	private PantryItemResponse toResponse(
			PantryItemEntity item,
			Map<String, PantryIngredientCatalogEntity> catalogByName,
			LocalDate today) {
		PantryIngredientCatalogEntity catalogEntry = catalogByName.get(item.getIngredientName());
		return new PantryItemResponse(
				item.getId(),
				item.getIngredientName(),
				catalogEntry == null ? "" : catalogEntry.getEmoji(),
				item.getUseByDate(),
				daysUntilExpiry(item.getUseByDate(), today));
	}

	private int daysUntilExpiry(LocalDate useByDate, LocalDate today) {
		return (int) ChronoUnit.DAYS.between(today, useByDate);
	}

	private String normalizeName(String value) {
		return value == null
				? ""
				: value.replaceAll("\\s+", "").toLowerCase(Locale.KOREAN);
	}

	private String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
