package com.cookpilot.backend.recipe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecipeApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 레시피_목록을_조회한다() throws Exception {
		mockMvc.perform(get("/api/v1/recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
				.andExpect(jsonPath("$[0].id").exists())
				.andExpect(jsonPath("$[0].title").exists())
				.andExpect(jsonPath("$[0].hasPersonalVersion").exists());
	}

	@Test
	void 레시피_상세를_조회한다() throws Exception {
		mockMvc.perform(get("/api/v1/recipes/" + RecipeService.RAMEN_RECIPE_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("라면"))
				.andExpect(jsonPath("$.steps", hasSize(3)))
				.andExpect(jsonPath("$.steps[0].instruction").value("물 500ml를 넣고 3분간 끓이세요."))
				.andExpect(jsonPath("$.steps[0].timerSeconds").value(180))
				.andExpect(jsonPath("$.ingredients", hasSize(4)));
	}

	@Test
	void 없는_레시피는_404를_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/recipes/99999999-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}
}
