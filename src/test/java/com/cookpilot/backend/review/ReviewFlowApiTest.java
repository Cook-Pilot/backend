package com.cookpilot.backend.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.recipe.RecipeService;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 핵심 루프 검증: (프론트에서 조리 완료) -> 피드백 저장 -> 개인 버전 생성 -> 목록 배지 반영.
 * 서버에는 세션이 없으므로 리뷰가 조리 1회의 기록이다.
 * V2 부터 리뷰/개인 버전이 실제 postgres 에 영속되므로 db 프로파일 베이스를 쓴다.
 * 개인 버전이 생기는 테스트는 김치볶음밥 레시피만 사용한다(다른 테스트와 상태 간섭 방지).
 */
class ReviewFlowApiTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String submitReview(String bodyFields) throws Exception {
		return mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + RecipeService.FRIED_RICE_RECIPE_ID + "\"," + bodyFields + "}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	void 피드백_저장시_개인_버전이_생성된다() throws Exception {
		String reviewBody = submitReview("\"rating\":3,\"comment\":\"너무 짰다\",\"nextTimeNote\":\"김치를 덜 넣자\"");

		JsonNode review = objectMapper.readTree(reviewBody);
		String reviewId = review.get("id").asText();
		String versionId = review.get("createdPersonalVersionId").asText();

		mockMvc.perform(get("/api/v1/reviews/" + reviewId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rating").value(3))
				.andExpect(jsonPath("$.comment").value("너무 짰다"))
				.andExpect(jsonPath("$.recipeId").value(RecipeService.FRIED_RICE_RECIPE_ID.toString()));

		// 상세는 메타 + 합성 결과 + 원시 diff. 리뷰 직후 버전은 diff 없이 원본 그대로 합성된다.
		mockMvc.perform(get("/api/v1/personal-versions/" + versionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version.recipeId").value(RecipeService.FRIED_RICE_RECIPE_ID.toString()))
				.andExpect(jsonPath("$.version.sourceReviewId").value(reviewId))
				.andExpect(jsonPath("$.ingredients", hasSize(4)))
				.andExpect(jsonPath("$.ingredients[0].origin").value("ORIGINAL"))
				.andExpect(jsonPath("$.steps", hasSize(4)))
				.andExpect(jsonPath("$.ingredientAdjustments", hasSize(0)))
				.andExpect(jsonPath("$.stepAdjustments", hasSize(0)));

		mockMvc.perform(get("/api/v1/recipes/" + RecipeService.FRIED_RICE_RECIPE_ID + "/personal-versions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
	}

	@Test
	void 레시피별_리뷰_목록을_조회한다() throws Exception {
		submitReview("\"rating\":5,\"comment\":\"좋았다\"");

		mockMvc.perform(get("/api/v1/recipes/" + RecipeService.FRIED_RICE_RECIPE_ID + "/reviews"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
	}

	@Test
	void 피드백_저장_후_레시피_목록에_내_버전_배지가_보인다() throws Exception {
		submitReview("\"rating\":4,\"comment\":\"괜찮았다\"");

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
	void 잘못된_rating은_400() throws Exception {
		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + RecipeService.FRIED_RICE_RECIPE_ID + "\",\"rating\":6}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void recipeId_없으면_400() throws Exception {
		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":3,\"comment\":\"레시피 없음\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 없는_레시피에_피드백을_남기면_404() throws Exception {
		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"99999999-0000-0000-0000-000000000000\",\"rating\":3}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 없는_리뷰_조회는_404() throws Exception {
		mockMvc.perform(get("/api/v1/reviews/99999999-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}
}
