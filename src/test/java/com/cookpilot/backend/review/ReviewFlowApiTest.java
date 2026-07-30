package com.cookpilot.backend.review;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 리뷰 API. 저장·멱등성·조회만 다룬다.
 *
 * 개인 버전 생성은 이 경로에서 분리됐다. 실행 스냅샷을 diff 로 역산하던 테스트
 * (원본 누락 400, ADD anchor/sortOrder 보존, 중복 버전 방지 등)는 그 HTTP 경로가
 * 사라져 성립하지 않으므로 함께 제거했다. 수정 파이프라인 엔드포인트가 생기면
 * 그 위에서 다시 작성한다.
 */
class ReviewFlowApiTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PostCookReviewRepository reviewRepository;

	@Autowired
	private PersonalRecipeVersionRepository personalRecipeVersionRepository;

	@Test
	void 리뷰_저장은_개인_버전을_만들지_않는다() throws Exception {
		JsonNode review = submitReview(UUID.randomUUID(), 1);

		assertThat(review.get("createdPersonalVersionId").isNull()).isTrue();
		assertThat(personalRecipeVersionRepository
				.findBySourceReviewIdIn(List.of(UUID.fromString(review.get("id").asText()))))
				.isEmpty();
	}

	@Test
	void 저장한_리뷰를_그대로_돌려준다() throws Exception {
		JsonNode review = submitReview(UUID.randomUUID(), 2);

		assertThat(review.get("targetServings").decimalValue()).isEqualByComparingTo("2");
		assertThat(review.get("rating").asInt()).isEqualTo(4);
		assertThat(review.get("comment").asText()).isEqualTo("맛있었다");
		assertThat(review.get("nextTimeNote").asText()).isEqualTo("다음에도 이대로");
		assertThat(review.get("cookedAt").asText()).isEqualTo("2026-07-26T01:00:00Z");

		mockMvc.perform(get("/api/v1/reviews/" + review.get("id").asText()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(review.get("id").asText()));
	}

	@Test
	void 동일한_조리_세션을_재전송하면_한_번만_저장한다() throws Exception {
		UUID clientSessionId = UUID.randomUUID();
		JsonNode first = submitReview(clientSessionId, 1);
		JsonNode retried = submitReview(clientSessionId, 1);

		assertThat(retried.get("id").asText()).isEqualTo(first.get("id").asText());
	}

	@Test
	void 동일한_조리_세션을_동시에_전송해도_한_번만_저장한다() throws Exception {
		UUID clientSessionId = UUID.randomUUID();
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<JsonNode> firstRequest = executor.submit(() -> {
				start.await();
				return submitReview(clientSessionId, 1);
			});
			Future<JsonNode> secondRequest = executor.submit(() -> {
				start.await();
				return submitReview(clientSessionId, 1);
			});

			start.countDown();
			JsonNode first = firstRequest.get(10, TimeUnit.SECONDS);
			JsonNode second = secondRequest.get(10, TimeUnit.SECONDS);

			assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());
			assertThat(reviewRepository.findByUserIdAndClientSessionId(
					UUID.fromString("00000000-0000-0000-0000-000000000001"),
					clientSessionId)).isPresent();
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 레시피별_리뷰와_기간별_조리_이력을_조회한다() throws Exception {
		JsonNode review = submitReview(UUID.randomUUID(), 1);

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

	private JsonNode submitReview(UUID clientSessionId, int targetServings) throws Exception {
		String body = """
				{
				  "clientSessionId": "%s",
				  "recipeId": "%s",
				  "cookedAt": "2026-07-26T01:00:00Z",
				  "targetServings": %d,
				  "rating": 4,
				  "comment": "맛있었다",
				  "nextTimeNote": "다음에도 이대로"
				}
				""".formatted(clientSessionId, TestRecipeIds.FRIED_RICE_RECIPE_ID, targetServings);
		String response = mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response);
	}
}
