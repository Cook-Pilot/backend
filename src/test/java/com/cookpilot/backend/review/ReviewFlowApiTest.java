package com.cookpilot.backend.review;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewFlowApiTest extends PostgresApiTestBase {

	private static final String ING_RICE = "20000000-0000-0000-0000-000000000201";
	private static final String ING_KIMCHI = "20000000-0000-0000-0000-000000000202";
	private static final String ING_OIL = "20000000-0000-0000-0000-000000000203";
	private static final String ING_EGG = "20000000-0000-0000-0000-000000000204";
	private static final String STEP_HEAT = "30000000-0000-0000-0000-000000000201";
	private static final String STEP_KIMCHI = "30000000-0000-0000-0000-000000000202";
	private static final String STEP_RICE = "30000000-0000-0000-0000-000000000203";
	private static final String STEP_SERVE = "30000000-0000-0000-0000-000000000204";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void 실행_변경이_있을_때만_개인_버전을_생성한다() throws Exception {
		JsonNode unchanged = submitReview(UUID.randomUUID(), 1, 1, 100, 1, 1);
		assertThat(unchanged.get("createdPersonalVersionId").isNull()).isTrue();

		JsonNode changed = submitReview(UUID.randomUUID(), 1, 1, 80, 1, 1);
		String reviewId = changed.get("id").asText();
		String versionId = changed.get("createdPersonalVersionId").asText();

		mockMvc.perform(get("/api/v1/personal-versions/" + versionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version.sourceReviewId").value(reviewId))
				.andExpect(jsonPath("$.ingredients", hasSize(4)))
				.andExpect(jsonPath("$.ingredientAdjustments", hasSize(1)))
				.andExpect(jsonPath("$.ingredientAdjustments[0].type").value("MODIFY"))
				.andExpect(jsonPath("$.ingredientAdjustments[0].amount").value(80));
	}

	@Test
	void 인분에_맞춘_단순_비례_증가는_개인_변경이_아니다() throws Exception {
		JsonNode review = submitReview(UUID.randomUUID(), 2, 2, 200, 2, 2);

		assertThat(review.get("targetServings").decimalValue()).isEqualByComparingTo("2");
		assertThat(review.get("createdPersonalVersionId").isNull()).isTrue();
	}

	@Test
	void 동일한_조리_세션을_재전송하면_한_번만_저장한다() throws Exception {
		UUID clientSessionId = UUID.randomUUID();
		JsonNode first = submitReview(clientSessionId, 1, 1, 70, 1, 1);
		JsonNode retried = submitReview(clientSessionId, 1, 1, 70, 1, 1);

		assertThat(retried.get("id").asText()).isEqualTo(first.get("id").asText());
		assertThat(retried.get("createdPersonalVersionId").asText())
				.isEqualTo(first.get("createdPersonalVersionId").asText());
	}

	@Test
	void 선택한_개인_버전을_그대로_실행하면_중복_버전을_만들지_않는다() throws Exception {
		JsonNode first = submitReview(UUID.randomUUID(), 1, 1, 75, 1, 1);
		String sourceVersionId = first.get("createdPersonalVersionId").asText();

		JsonNode repeated = submitReview(
				UUID.randomUUID(), 1, 1, 75, 1, 1, sourceVersionId);

		assertThat(repeated.get("sourcePersonalVersionId").asText()).isEqualTo(sourceVersionId);
		assertThat(repeated.get("createdPersonalVersionId").isNull()).isTrue();
	}

	@Test
	void 레시피별_리뷰와_기간별_조리_이력을_조회한다() throws Exception {
		JsonNode review = submitReview(UUID.randomUUID(), 1, 1, 90, 1, 1);

		mockMvc.perform(get("/api/v1/recipes/" + TestRecipeIds.FRIED_RICE_RECIPE_ID + "/reviews"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

		mockMvc.perform(get("/api/v1/cooking-history")
						.param("from", "2026-01-01T00:00:00Z")
						.param("to", "2027-01-01T00:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
				.andExpect(jsonPath("$[?(@.reviewId == '" + review.get("id").asText()
						+ "')].recipeTitle").value("김치볶음밥"));
	}

	@Test
	void 개인_버전_목록은_최근_5개까지만_반환한다() throws Exception {
		for (int i = 0; i < 6; i++) {
			submitReview(UUID.randomUUID(), 1, 1, 60 + i, 1, 1);
		}

		mockMvc.perform(get("/api/v1/recipes/" + TestRecipeIds.FRIED_RICE_RECIPE_ID
						+ "/personal-versions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)));
	}

	@Test
	void 잘못된_입력과_없는_리소스는_각각_400과_404() throws Exception {
		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "%s",
								  "rating": 6
								}
								""".formatted(UUID.randomUUID(), TestRecipeIds.FRIED_RICE_RECIPE_ID)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "99999999-0000-0000-0000-000000000000",
								  "rating": 3
								}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/reviews/99999999-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}

	private JsonNode submitReview(
			UUID clientSessionId,
			int targetServings,
			int riceAmount,
			int kimchiAmount,
			int oilAmount,
			int eggAmount) throws Exception {
		return submitReview(clientSessionId, targetServings, riceAmount, kimchiAmount,
				oilAmount, eggAmount, null);
	}

	private JsonNode submitReview(
			UUID clientSessionId,
			int targetServings,
			int riceAmount,
			int kimchiAmount,
			int oilAmount,
			int eggAmount,
			String sourcePersonalVersionId) throws Exception {
		String body = """
				{
				  "clientSessionId": "%s",
				  "recipeId": "%s",
				  "cookedAt": "2026-07-26T01:00:00Z",
				  "targetServings": %d,
				  "sourcePersonalVersionId": %s,
				  "rating": 4,
				  "comment": "맛있었다",
				  "nextTimeNote": "다음에도 이대로",
				  "ingredients": [
				    {"originalIngredientId":"%s","name":"밥","amount":%d,"unit":"공기","required":true,"omitted":false,"sortOrder":0},
				    {"originalIngredientId":"%s","name":"김치","amount":%d,"unit":"g","required":true,"omitted":false,"sortOrder":1},
				    {"originalIngredientId":"%s","name":"식용유","amount":%d,"unit":"큰술","required":true,"omitted":false,"sortOrder":2},
				    {"originalIngredientId":"%s","name":"계란","amount":%d,"unit":"개","required":false,"omitted":false,"sortOrder":3}
				  ],
				  "steps": [
				    {"originalStepId":"%s","instruction":"팬에 기름을 두르고 중불로 달구세요.","timerSeconds":60,"cautionNote":"기름이 튈 수 있어요","sortOrder":0},
				    {"originalStepId":"%s","instruction":"김치를 넣고 2분간 볶으세요.","timerSeconds":120,"sortOrder":1},
				    {"originalStepId":"%s","instruction":"밥을 넣고 3분간 볶으세요.","timerSeconds":180,"sortOrder":2},
				    {"originalStepId":"%s","instruction":"불을 끄고 그릇에 담으세요.","sortOrder":3}
				  ]
				}
				""".formatted(
				clientSessionId,
				TestRecipeIds.FRIED_RICE_RECIPE_ID,
				targetServings,
				sourcePersonalVersionId == null ? "null" : "\"" + sourcePersonalVersionId + "\"",
				ING_RICE, riceAmount,
				ING_KIMCHI, kimchiAmount,
				ING_OIL, oilAmount,
				ING_EGG, eggAmount,
				STEP_HEAT, STEP_KIMCHI, STEP_RICE, STEP_SERVE);
		String response = mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response);
	}
}
