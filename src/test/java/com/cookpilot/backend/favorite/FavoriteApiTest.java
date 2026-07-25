package com.cookpilot.backend.favorite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FavoriteApiTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecipeFavoriteRepository recipeFavoriteRepository;

	@BeforeEach
	void clearFavorites() {
		recipeFavoriteRepository.deleteAll();
	}

	@Test
	void 신규_사용자의_즐겨찾기는_빈_목록이다() throws Exception {
		mockMvc.perform(get("/api/v1/favorites"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void 즐겨찾기를_추가하고_중복없이_조회하고_삭제한다() throws Exception {
		String path = "/api/v1/recipes/" + TestRecipeIds.RAMEN_RECIPE_ID + "/favorite";

		mockMvc.perform(put(path))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(TestRecipeIds.RAMEN_RECIPE_ID.toString()))
				.andExpect(jsonPath("$.favoritedAt").exists());
		mockMvc.perform(put(path))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/favorites"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(TestRecipeIds.RAMEN_RECIPE_ID.toString()));

		mockMvc.perform(get("/api/v1/recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(
						"$[?(@.id == '" + TestRecipeIds.RAMEN_RECIPE_ID + "')].favorite")
						.value(contains(true)));

		mockMvc.perform(delete(path))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete(path))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/favorites"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void 없는_레시피_즐겨찾기는_404다() throws Exception {
		String path = "/api/v1/recipes/99999999-0000-0000-0000-000000000000/favorite";

		mockMvc.perform(put(path))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete(path))
				.andExpect(status().isNotFound());
	}
}
