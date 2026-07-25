package com.cookpilot.backend.recipe;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 레시피 목록/상세 API. 원본 레시피와 개인 버전 배지를 모두 PostgreSQL/JPA에서 조회한다.
 */
class RecipeApiTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecipeRepository recipeRepository;

	@Autowired
	private RecipeIngredientRepository ingredientRepository;

	@Autowired
	private RecipeStepRepository stepRepository;

	@Test
	void 레시피_목록을_조회한다() throws Exception {
		mockMvc.perform(get("/api/v1/recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
				.andExpect(jsonPath("$[0].id").exists())
				.andExpect(jsonPath("$[0].title").exists())
				.andExpect(jsonPath("$[0].hasPersonalVersion").exists())
				.andExpect(jsonPath("$[0].ingredients").doesNotExist())
				.andExpect(jsonPath("$[0].steps").doesNotExist());
	}

	@Test
	void 레시피_상세를_조회한다() throws Exception {
		mockMvc.perform(get("/api/v1/recipes/" + TestRecipeIds.RAMEN_RECIPE_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("라면"))
				.andExpect(jsonPath("$.baseServings").value(1.0))
				.andExpect(jsonPath("$.steps", hasSize(3)))
				.andExpect(jsonPath("$.steps[0].instruction").value("물 500ml를 넣고 3분간 끓이세요."))
				.andExpect(jsonPath("$.steps[0].timerSeconds").value(180))
				.andExpect(jsonPath("$.ingredients", hasSize(4)));
	}

	@Test
	void DB에만_저장한_레시피를_목록과_상세에서_조회한다() throws Exception {
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity(
				"DB 전용 된장국", "하드코딩 Map에는 없는 레시피", BigDecimal.valueOf(2),
				"https://cdn.cookpilot.app/recipes/doenjang.png"));
		ingredientRepository.save(new RecipeIngredientEntity(
				recipe.getId(), "물", BigDecimal.valueOf(500), "ml", true, 1));
		ingredientRepository.save(new RecipeIngredientEntity(
				recipe.getId(), "된장", BigDecimal.ONE, "큰술", true, 0));
		stepRepository.save(new RecipeStepEntity(
				recipe.getId(), 1, "두부를 넣고 마저 끓여요.", 180, "냄비가 뜨거워요"));
		stepRepository.save(new RecipeStepEntity(
				recipe.getId(), 0, "물을 끓이고 된장을 풀어요.", 120, null,
				"https://cdn.cookpilot.app/steps/doenjang-1.png"));

		mockMvc.perform(get("/api/v1/recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '" + recipe.getId() + "')]", hasSize(1)))
				.andExpect(jsonPath("$[?(@.id == '" + recipe.getId() + "')].imageUrl")
						.value(contains("https://cdn.cookpilot.app/recipes/doenjang.png")));

		mockMvc.perform(get("/api/v1/recipes/" + recipe.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("DB 전용 된장국"))
				.andExpect(jsonPath("$.description").value("하드코딩 Map에는 없는 레시피"))
				.andExpect(jsonPath("$.imageUrl").value("https://cdn.cookpilot.app/recipes/doenjang.png"))
				.andExpect(jsonPath("$.ingredients[0].name").value("된장"))
				.andExpect(jsonPath("$.ingredients[1].name").value("물"))
				.andExpect(jsonPath("$.steps[0].instruction").value("물을 끓이고 된장을 풀어요."))
				.andExpect(jsonPath("$.steps[0].timerSeconds").value(120))
				.andExpect(jsonPath("$.steps[0].imageUrl")
						.value("https://cdn.cookpilot.app/steps/doenjang-1.png"))
				.andExpect(jsonPath("$.steps[1].cautionNote").value("냄비가 뜨거워요"));
	}

	@Test
	void 없는_레시피는_404를_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/recipes/99999999-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}
}
