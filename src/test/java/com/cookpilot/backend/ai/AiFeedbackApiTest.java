package com.cookpilot.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.recipe.RecipeService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 조리 진행은 프론트가 관리하므로, 어떤 레시피의 몇 번째 단계인지를 요청 본문으로 받는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiFeedbackApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void AI_피드백은_목데이터_응답을_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + RecipeService.RAMEN_RECIPE_ID
								+ "\",\"stepIndex\":0,\"userSpeech\":\"물이 안 끓어\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mock").value(true))
				.andExpect(jsonPath("$.speechText").exists())
				.andExpect(jsonPath("$.screenText").exists())
				.andExpect(jsonPath("$.suggestedAction.type").value("EXTEND_TIMER"))
				.andExpect(jsonPath("$.suggestedAction.seconds").value(60))
				.andExpect(jsonPath("$.eventPayload.currentStepIndex").value(0))
				.andExpect(jsonPath("$.eventPayload.userSpeech").value("물이 안 끓어"));
	}

	@Test
	void userSpeech_없으면_400() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + RecipeService.RAMEN_RECIPE_ID + "\",\"stepIndex\":0}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void recipeId_없으면_400() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"stepIndex\":0,\"userSpeech\":\"물이 안 끓어\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 없는_레시피면_404() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"99999999-0000-0000-0000-000000000000\","
								+ "\"stepIndex\":0,\"userSpeech\":\"물이 안 끓어\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 없는_단계면_404() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + RecipeService.RAMEN_RECIPE_ID
								+ "\",\"stepIndex\":99,\"userSpeech\":\"물이 안 끓어\"}"))
				.andExpect(status().isNotFound());
	}
}
