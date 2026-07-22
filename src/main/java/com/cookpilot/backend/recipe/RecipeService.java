package com.cookpilot.backend.recipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.common.NotFoundException;

/**
 * repository 계층 미확정. MVP 데모용 레시피를 인메모리 seed로 제공한다.
 */
@Service
public class RecipeService {

	public static final UUID RAMEN_RECIPE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	public static final UUID FRIED_RICE_RECIPE_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");

	private final Map<UUID, Recipe> recipes = new LinkedHashMap<>();

	public RecipeService() {
		recipes.put(RAMEN_RECIPE_ID, new Recipe(
				RAMEN_RECIPE_ID,
				"라면",
				"MVP 데모 시나리오용 기본 라면 레시피",
				null,
				List.of(
						new RecipeIngredient("라면", 1.0, "봉", true),
						new RecipeIngredient("물", 500.0, "ml", true),
						new RecipeIngredient("계란", 1.0, "개", false),
						new RecipeIngredient("파", 0.5, "대", false)
				),
				List.of(
						new RecipeStep(0, "물 500ml를 넣고 3분간 끓이세요.", 180, null, null),
						new RecipeStep(1, "건더기, 분말스프, 면을 넣고 3분간 끓이세요.", 180, "끓어 넘침 주의", null),
						new RecipeStep(2, "불을 끄고 그릇에 옮겨 담으세요.", null, "뜨거우니 조심하세요", null)
				)
		));
		recipes.put(FRIED_RICE_RECIPE_ID, new Recipe(
				FRIED_RICE_RECIPE_ID,
				"김치볶음밥",
				"기본 김치볶음밥 레시피",
				null,
				List.of(
						new RecipeIngredient("밥", 1.0, "공기", true),
						new RecipeIngredient("김치", 100.0, "g", true),
						new RecipeIngredient("식용유", 1.0, "큰술", true),
						new RecipeIngredient("계란", 1.0, "개", false)
				),
				List.of(
						new RecipeStep(0, "팬에 기름을 두르고 중불로 달구세요.", 60, "기름이 튈 수 있어요", null),
						new RecipeStep(1, "김치를 넣고 2분간 볶으세요.", 120, null, null),
						new RecipeStep(2, "밥을 넣고 3분간 볶으세요.", 180, null, null),
						new RecipeStep(3, "불을 끄고 그릇에 담으세요.", null, null, null)
				)
		));
	}

	public List<Recipe> findAll() {
		return List.copyOf(recipes.values());
	}

	public Recipe findById(UUID recipeId) {
		Recipe recipe = recipes.get(recipeId);
		if (recipe == null) {
			throw new NotFoundException("레시피를 찾을 수 없습니다: " + recipeId);
		}
		return recipe;
	}
}
