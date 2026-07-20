package com.cookpilot.backend.cooksession;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.cookpilot.backend.recipe.RecipeService;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CookSessionApiTest {

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
		JsonNode node = objectMapper.readTree(body);
		return node.get("id").asText();
	}

	@Test
	void 세션을_생성하면_COOKING_상태와_스냅샷이_반환된다() throws Exception {
		mockMvc.perform(post("/api/v1/cook-sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + RecipeService.RAMEN_RECIPE_ID + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("COOKING"))
				.andExpect(jsonPath("$.currentStepIndex").value(0))
				.andExpect(jsonPath("$.recipeTitle").value("라면"))
				.andExpect(jsonPath("$.totalSteps").value(3))
				.andExpect(jsonPath("$.currentStep.instruction").value("물 500ml를 넣고 3분간 끓이세요."));
	}

	@Test
	void recipeId_없이_세션_생성하면_400() throws Exception {
		mockMvc.perform(post("/api/v1/cook-sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 없는_레시피로_세션_생성하면_404() throws Exception {
		mockMvc.perform(post("/api/v1/cook-sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"99999999-0000-0000-0000-000000000000\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 다음_이전_단계로_이동한다() throws Exception {
		String sessionId = createSession();

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/step")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"direction\":\"NEXT\",\"source\":\"VOICE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStepIndex").value(1))
				.andExpect(jsonPath("$.currentStep.instruction").value("건더기, 분말스프, 면을 넣고 3분간 끓이세요."));

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/step")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"direction\":\"PREV\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStepIndex").value(0));
	}

	@Test
	void 첫_단계에서_이전으로_이동하면_400() throws Exception {
		String sessionId = createSession();

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/step")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"direction\":\"PREV\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 조리_완료하면_REVIEW_상태가_된다() throws Exception {
		String sessionId = createSession();

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/complete"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVIEW"))
				.andExpect(jsonPath("$.completedAt").exists());
	}

	@Test
	void 중단하면_ABORTED_상태가_되고_이후_단계이동은_409() throws Exception {
		String sessionId = createSession();

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/abort"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ABORTED"));

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/step")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"direction\":\"NEXT\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void 이벤트를_기록하고_조회한다() throws Exception {
		String sessionId = createSession();

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"eventType\":\"TIMER_EXTENDED\",\"stepIndex\":0,\"source\":\"VOICE\","
								+ "\"payload\":{\"seconds\":60}}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventType").value("TIMER_EXTENDED"))
				.andExpect(jsonPath("$.payload.seconds").value(60));

		// SESSION_STARTED + TIMER_EXTENDED
		mockMvc.perform(get("/api/v1/cook-sessions/" + sessionId + "/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
				.andExpect(jsonPath("$[0].eventType").value("SESSION_STARTED"));
	}

	@Test
	void 없는_세션_조회는_404() throws Exception {
		mockMvc.perform(get("/api/v1/cook-sessions/99999999-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}
}
