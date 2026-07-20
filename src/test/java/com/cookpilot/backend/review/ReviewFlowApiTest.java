package com.cookpilot.backend.review;

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

/**
 * 핵심 루프 검증: 세션 생성 -> 조리 완료 -> 피드백 저장 -> 개인 버전 생성 -> 목록 배지 반영.
 * 개인 버전이 생기는 테스트는 김치볶음밥 레시피만 사용한다(다른 테스트와 상태 간섭 방지).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewFlowApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String createCompletedSession() throws Exception {
		String body = mockMvc.perform(post("/api/v1/cook-sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + RecipeService.FRIED_RICE_RECIPE_ID + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = objectMapper.readTree(body).get("id").asText();
		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/complete"))
				.andExpect(status().isOk());
		return sessionId;
	}

	@Test
	void 피드백_저장시_개인_버전이_생성되고_세션은_COMPLETED가_된다() throws Exception {
		String sessionId = createCompletedSession();

		String reviewBody = mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/review")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":3,\"comment\":\"너무 짰다\",\"nextTimeNote\":\"김치를 덜 넣자\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.rating").value(3))
				.andExpect(jsonPath("$.comment").value("너무 짰다"))
				.andExpect(jsonPath("$.createdPersonalVersionId").exists())
				.andReturn().getResponse().getContentAsString();

		JsonNode review = objectMapper.readTree(reviewBody);
		String versionId = review.get("createdPersonalVersionId").asText();

		mockMvc.perform(get("/api/v1/cook-sessions/" + sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"));

		mockMvc.perform(get("/api/v1/personal-versions/" + versionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recipeId").value(RecipeService.FRIED_RICE_RECIPE_ID.toString()))
				.andExpect(jsonPath("$.sourceSessionId").value(sessionId))
				.andExpect(jsonPath("$.adjustmentPayload.source").value("MOCK"));

		mockMvc.perform(get("/api/v1/recipes/" + RecipeService.FRIED_RICE_RECIPE_ID + "/personal-versions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
	}

	@Test
	void 피드백_저장_후_레시피_목록에_내_버전_배지가_보인다() throws Exception {
		String sessionId = createCompletedSession();
		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/review")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":4,\"comment\":\"괜찮았다\"}"))
				.andExpect(status().isCreated());

		String listBody = mockMvc.perform(get("/api/v1/recipes"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		JsonNode list = objectMapper.readTree(listBody);
		boolean friedRiceHasVersion = false;
		for (JsonNode item : list) {
			if (item.get("id").asText().equals(RecipeService.FRIED_RICE_RECIPE_ID.toString())) {
				friedRiceHasVersion = item.get("hasPersonalVersion").asBoolean();
			}
		}
		org.assertj.core.api.Assertions.assertThat(friedRiceHasVersion).isTrue();
	}

	@Test
	void 조리_완료_전_피드백은_409() throws Exception {
		String body = mockMvc.perform(post("/api/v1/cook-sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + RecipeService.FRIED_RICE_RECIPE_ID + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = objectMapper.readTree(body).get("id").asText();

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/review")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":3,\"comment\":\"미리 리뷰\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void 잘못된_rating은_400() throws Exception {
		String sessionId = createCompletedSession();

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/review")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":6}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 중복_피드백은_409() throws Exception {
		String sessionId = createCompletedSession();

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/review")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":5,\"comment\":\"좋았다\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/cook-sessions/" + sessionId + "/review")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":1,\"comment\":\"중복\"}"))
				.andExpect(status().isConflict());
	}
}
