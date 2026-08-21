package com.cookpilot.backend.recipe;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.cookpilot.backend.tag.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;

@Service
@Transactional(readOnly = true)
public class RecipeService {

	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final RecipeStepRepository recipeStepRepository;
	private final TagRepository tagRepository;

	public RecipeService(RecipeRepository recipeRepository,
			RecipeIngredientRepository recipeIngredientRepository,
			RecipeStepRepository recipeStepRepository,
			TagRepository tagRepository) {
		this.recipeRepository = recipeRepository;
		this.recipeIngredientRepository = recipeIngredientRepository;
		this.recipeStepRepository = recipeStepRepository;
		this.tagRepository = tagRepository;
	}

	public List<RecipeOverview> findAll() {
		return recipeRepository.findByStatusOrderByTitleAscIdAsc("active").stream()
				.map(this::toOverview)
				.toList();
	}

	public Page<RecipeOverview> search(
			String title, String ingredient, List<String> tags, int page, int size) {
		String normalizedTitle = escapeLikePattern(title == null ? "" : title.trim());
		String normalizedIngredient = escapeLikePattern(ingredient == null ? "" : ingredient.trim());
		int normalizedPage = Math.max(page, 1);
		int normalizedSize = Math.min(Math.max(size, 1), 50);

		List<String> tagCodes = normalizeTags(tags);
		// 축 안은 OR, 축 사이는 AND (V14). 레시피가 만족해야 할 축의 개수가 곧 이 값이다.
		// 태그가 없으면 0 이고 질의의 태그 조건이 통째로 꺼진다.
		long tagAxisCount = tagCodes.isEmpty() ? 0 : tagRepository.countAxes(tagCodes);
		// IN 절에 빈 목록을 넘기면 JPQL 이 깨진다. 조건이 꺼져 있으므로 값은 쓰이지 않는다.
		List<String> inClause = tagCodes.isEmpty() ? List.of("") : tagCodes;

		Page<RecipeEntity> result = recipeRepository.search(
				"active",
				normalizedTitle,
				normalizedIngredient,
				inClause,
				tagAxisCount,
				PageRequest.of(normalizedPage - 1, normalizedSize));
		int lastPage = Math.max(result.getTotalPages(), 1);
		if (normalizedPage > lastPage) {
			result = recipeRepository.search(
					"active",
					normalizedTitle,
					normalizedIngredient,
					inClause,
					tagAxisCount,
					PageRequest.of(lastPage - 1, normalizedSize));
		}
		return result.map(this::toOverview);
	}

	/**
	 * 사전에 없는 코드는 400 으로 돌려준다.
	 *
	 * 조용히 무시하면 오타 하나가 "필터가 안 걸린 전체 목록"이 되어 클라이언트는 성공으로 읽는다.
	 * 태그 코드는 URL 에 그대로 나가는 값이라 오타가 실제로 생긴다.
	 */
	private List<String> normalizeTags(List<String> tags) {
		if (tags == null) {
			return List.of();
		}
		List<String> requested = tags.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(code -> !code.isEmpty())
				.distinct()
				.toList();
		if (requested.isEmpty()) {
			return List.of();
		}
		Set<String> known = Set.copyOf(tagRepository.findExistingCodes(requested));
		List<String> unknown = requested.stream().filter(code -> !known.contains(code)).toList();
		if (!unknown.isEmpty()) {
			throw new IllegalArgumentException("없는 태그 코드입니다: " + String.join(", ", unknown));
		}
		return requested;
	}

	private String escapeLikePattern(String value) {
		return value
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
	}

	public Recipe findById(UUID recipeId) {
		RecipeEntity entity = recipeRepository.findById(recipeId)
				.orElseThrow(() -> new NotFoundException("레시피를 찾을 수 없습니다: " + recipeId));
		return toRecipe(entity);
	}

	private RecipeOverview toOverview(RecipeEntity entity) {
		return new RecipeOverview(
				entity.getId(),
				entity.getTitle(),
				entity.getDescription(),
				entity.getImageUrl());
	}

	private Recipe toRecipe(RecipeEntity entity) {
		List<RecipeIngredient> ingredients = recipeIngredientRepository
				.findByRecipeIdOrderBySortOrderAsc(entity.getId())
				.stream()
				.map(ingredient -> new RecipeIngredient(
						ingredient.getId(),
						ingredient.getName(),
						ingredient.getAmount() == null ? null : ingredient.getAmount().doubleValue(),
						ingredient.getUnit(),
						ingredient.isRequired()))
				.toList();

		List<RecipeStep> steps = recipeStepRepository
				.findByRecipeIdOrderByStepIndexAsc(entity.getId())
				.stream()
				.map(step -> new RecipeStep(
						step.getId(),
						step.getStepIndex(),
						step.getInstruction(),
						step.getTimerSeconds(),
						step.getCautionNote(),
						step.getImageUrl()))
				.toList();

		return new Recipe(
				entity.getId(),
				entity.getTitle(),
				entity.getDescription(),
				entity.getBaseServings().doubleValue(),
				entity.getImageUrl(),
				ingredients,
				steps,
				entity.getSourceType(),
				entity.getSourceRef());
	}
}
