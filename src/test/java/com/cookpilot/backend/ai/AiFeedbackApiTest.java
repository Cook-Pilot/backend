package com.cookpilot.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.cookpilot.backend.recipe.RecipeService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiFeedbackApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String createSession() throws Exception {
		String body = mockMvc.perform(post("/api/v1/cook-sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + RecipeService.RAMEN_RECIPE_ID + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("id").asText();
	}

	@Test
	void AI_피드백은_목데이터_응답을_반환하고_이벤트를_남긴다() throws Exception {
		String sessionId = createSession();

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"userSpeech\":\"물이 안 끓어\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mock").value(true))
				.andExpect(jsonPath("$.speechText").exists())
				.andExpect(jsonPath("$.screenText").exists())
				.andExpect(jsonPath("$.suggestedAction.type").value("EXTEND_TIMER"))
				.andExpect(jsonPath("$.suggestedAction.seconds").value(60))
				.andExpect(jsonPath("$.eventPayload.userSpeech").value("물이 안 끓어"));

		mockMvc.perform(get("/api/v1/cook-sessions/" + sessionId + "/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.eventType == 'AI_FEEDBACK_REQUESTED')]").exists());
	}

	@Test
	void userSpeech_없으면_400() throws Exception {
		String sessionId = createSession();

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 없는_세션이면_404() throws Exception {
		mockMvc.perform(post("/api/v1/cook-sessions/99999999-0000-0000-0000-000000000000/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"userSpeech\":\"물이 안 끓어\"}"))
				.andExpect(status().isNotFound());
	}
}
