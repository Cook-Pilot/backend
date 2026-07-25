package com.cookpilot.backend.favorite;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.personalrecipe.PersonalRecipeService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersion;
import com.cookpilot.backend.recipe.RecipeEntity;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.user.UserService;

@Service
@Transactional(readOnly = true)
public class FavoriteService {

	private final RecipeFavoriteRepository recipeFavoriteRepository;
	private final RecipeRepository recipeRepository;
	private final PersonalRecipeService personalRecipeService;
	private final UserService userService;

	public FavoriteService(RecipeFavoriteRepository recipeFavoriteRepository,
			RecipeRepository recipeRepository,
			PersonalRecipeService personalRecipeService,
			UserService userService) {
		this.recipeFavoriteRepository = recipeFavoriteRepository;
		this.recipeRepository = recipeRepository;
		this.personalRecipeService = personalRecipeService;
		this.userService = userService;
	}

	public List<FavoriteRecipeResponse> findAll() {
		UUID userId = userService.getCurrentUser().id();
		List<RecipeFavoriteEntity> favorites =
				recipeFavoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
		if (favorites.isEmpty()) {
			return List.of();
		}

		List<UUID> recipeIds = favorites.stream()
				.map(RecipeFavoriteEntity::getRecipeId)
				.toList();
		Map<UUID, RecipeEntity> recipesById = recipeRepository.findAllById(recipeIds).stream()
				.collect(Collectors.toMap(RecipeEntity::getId, recipe -> recipe));
		Map<UUID, PersonalRecipeVersion> latestByRecipe =
				personalRecipeService.findLatestByRecipes(recipeIds);

		return favorites.stream()
				.filter(favorite -> recipesById.containsKey(favorite.getRecipeId()))
				.map(favorite -> toResponse(
						favorite,
						recipesById.get(favorite.getRecipeId()),
						latestByRecipe.get(favorite.getRecipeId())))
				.toList();
	}

	public Set<UUID> findFavoriteRecipeIds(Collection<UUID> recipeIds) {
		if (recipeIds.isEmpty()) {
			return Set.of();
		}
		UUID userId = userService.getCurrentUser().id();
		return recipeFavoriteRepository.findByUserIdAndRecipeIdIn(userId, recipeIds).stream()
				.map(RecipeFavoriteEntity::getRecipeId)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Transactional
	public FavoriteRecipeResponse add(UUID recipeId) {
		RecipeEntity recipe = findRecipe(recipeId);
		UUID userId = userService.getCurrentUser().id();
		recipeFavoriteRepository.insertIgnore(UUID.randomUUID(), userId, recipeId);
		RecipeFavoriteEntity favorite = recipeFavoriteRepository
				.findByUserIdAndRecipeId(userId, recipeId)
				.orElseThrow(() -> new IllegalStateException("즐겨찾기 저장 결과를 찾을 수 없습니다."));
		PersonalRecipeVersion latest = personalRecipeService.findLatestByRecipe(recipeId)
				.orElse(null);
		return toResponse(favorite, recipe, latest);
	}

	@Transactional
	public void remove(UUID recipeId) {
		findRecipe(recipeId);
		UUID userId = userService.getCurrentUser().id();
		recipeFavoriteRepository.deleteByUserIdAndRecipeId(userId, recipeId);
	}

	private RecipeEntity findRecipe(UUID recipeId) {
		return recipeRepository.findById(recipeId)
				.orElseThrow(() -> new NotFoundException("레시피를 찾을 수 없습니다: " + recipeId));
	}

	private FavoriteRecipeResponse toResponse(
			RecipeFavoriteEntity favorite,
			RecipeEntity recipe,
			PersonalRecipeVersion latest) {
		return new FavoriteRecipeResponse(
				recipe.getId(),
				recipe.getTitle(),
				recipe.getDescription(),
				recipe.getImageUrl(),
				favorite.getCreatedAt(),
				latest != null,
				latest == null ? null : latest.id());
	}
}
