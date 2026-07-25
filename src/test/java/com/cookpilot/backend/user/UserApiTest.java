package com.cookpilot.backend.user;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void 익명_사용자를_순서대로_발급하고_헤더로_다시_조회한다() throws Exception {
		String firstBody = mockMvc.perform(post("/api/v1/users/anonymous"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").isEmpty())
				.andExpect(jsonPath("$.anonymous").value(true))
				.andReturn()
				.getResponse()
				.getContentAsString();

		String secondBody = mockMvc.perform(post("/api/v1/users/anonymous"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.anonymous").value(true))
				.andReturn()
				.getResponse()
				.getContentAsString();

		JsonNode first = objectMapper.readTree(firstBody);
		JsonNode second = objectMapper.readTree(secondBody);
		long firstNumber = first.get("betaNumber").asLong();
		long secondNumber = second.get("betaNumber").asLong();
		String firstId = first.get("id").asText();

		org.assertj.core.api.Assertions.assertThat(secondNumber).isGreaterThan(firstNumber);

		mockMvc.perform(get("/api/v1/users/me")
						.header(UserService.USER_ID_HEADER, firstId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(firstId))
				.andExpect(jsonPath("$.displayName").value("베타 사용자 " + firstNumber))
				.andExpect(jsonPath("$.betaNumber").value(firstNumber))
				.andExpect(jsonPath("$.anonymous").value(true));
	}

	@Test
	void 같은_멱등성_키로_재시도하면_같은_익명_사용자를_반환한다() throws Exception {
		String installationId = "91000000-0000-4000-8000-000000000001";

		String firstBody = mockMvc.perform(post("/api/v1/users/anonymous")
						.header(UserService.IDEMPOTENCY_KEY_HEADER, installationId))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		String retryBody = mockMvc.perform(post("/api/v1/users/anonymous")
						.header(UserService.IDEMPOTENCY_KEY_HEADER, installationId))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		JsonNode first = objectMapper.readTree(firstBody);
		JsonNode retry = objectMapper.readTree(retryBody);

		org.assertj.core.api.Assertions.assertThat(retry.get("id").asText())
				.isEqualTo(first.get("id").asText());
		org.assertj.core.api.Assertions.assertThat(retry.get("betaNumber").asLong())
				.isEqualTo(first.get("betaNumber").asLong());
	}

	@Test
	void 같은_멱등성_키의_동시_요청도_같은_사용자를_반환한다() throws Exception {
		String installationId = "91000000-0000-4000-8000-000000000002";
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<String> first = executor.submit(() -> createUserConcurrently(
					installationId, ready, start));
			Future<String> second = executor.submit(() -> createUserConcurrently(
					installationId, ready, start));
			org.assertj.core.api.Assertions.assertThat(
					ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			JsonNode firstUser = objectMapper.readTree(first.get(10, TimeUnit.SECONDS));
			JsonNode secondUser = objectMapper.readTree(second.get(10, TimeUnit.SECONDS));

			org.assertj.core.api.Assertions.assertThat(secondUser.get("id").asText())
					.isEqualTo(firstUser.get("id").asText());
			org.assertj.core.api.Assertions.assertThat(secondUser.get("betaNumber").asLong())
					.isEqualTo(firstUser.get("betaNumber").asLong());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 사용자_헤더가_비어_있으면_개인화_요청을_거부한다() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
						.header(UserService.USER_ID_HEADER, ""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER_SESSION_REQUIRED"));
	}

	@Test
	void 존재하지_않는_사용자는_구조화된_오류_코드를_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
						.header(UserService.USER_ID_HEADER,
								"99999999-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void 익명_사용자마다_즐겨찾기가_분리된다() throws Exception {
		String firstUserBody = mockMvc.perform(post("/api/v1/users/anonymous"))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String secondUserBody = mockMvc.perform(post("/api/v1/users/anonymous"))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String firstUserId = objectMapper.readTree(firstUserBody).get("id").asText();
		String secondUserId = objectMapper.readTree(secondUserBody).get("id").asText();
		String favoritePath =
				"/api/v1/recipes/10000000-0000-0000-0000-000000000001/favorite";

		mockMvc.perform(put(favoritePath)
						.header(UserService.USER_ID_HEADER, firstUserId))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/favorites")
						.header(UserService.USER_ID_HEADER, firstUserId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get("/api/v1/favorites")
						.header(UserService.USER_ID_HEADER, secondUserId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 익명_사용자마다_후기와_개인_레시피가_분리된다() throws Exception {
		String firstUserBody = mockMvc.perform(post("/api/v1/users/anonymous"))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String secondUserBody = mockMvc.perform(post("/api/v1/users/anonymous"))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String firstUserId = objectMapper.readTree(firstUserBody).get("id").asText();
		String secondUserId = objectMapper.readTree(secondUserBody).get("id").asText();

		String reviewBody = mockMvc.perform(post("/api/v1/reviews")
						.header(UserService.USER_ID_HEADER, firstUserId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "recipeId": "%s",
								  "rating": 5,
								  "comment": "첫 번째 사용자 후기"
								}
								""".formatted(TestRecipeIds.RAMEN_RECIPE_ID)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode review = objectMapper.readTree(reviewBody);
		String reviewId = review.get("id").asText();
		String versionId = review.get("createdPersonalVersionId").asText();

		mockMvc.perform(get("/api/v1/recipes/"
						+ TestRecipeIds.RAMEN_RECIPE_ID + "/reviews")
						.header(UserService.USER_ID_HEADER, secondUserId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/v1/reviews/" + reviewId)
						.header(UserService.USER_ID_HEADER, secondUserId))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/recipes/"
						+ TestRecipeIds.RAMEN_RECIPE_ID + "/personal-versions")
						.header(UserService.USER_ID_HEADER, secondUserId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/v1/personal-versions/" + versionId)
						.header(UserService.USER_ID_HEADER, secondUserId))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/home/recent-recipes")
						.header(UserService.USER_ID_HEADER, secondUserId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		mockMvc.perform(get("/api/v1/reviews/" + reviewId)
						.header(UserService.USER_ID_HEADER, firstUserId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(reviewId));
		mockMvc.perform(get("/api/v1/personal-versions/" + versionId)
						.header(UserService.USER_ID_HEADER, firstUserId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version.id").value(versionId));
		mockMvc.perform(get("/api/v1/home/recent-recipes")
						.header(UserService.USER_ID_HEADER, firstUserId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id")
						.value(TestRecipeIds.RAMEN_RECIPE_ID.toString()));
	}

	private String createUserConcurrently(
			String installationId,
			CountDownLatch ready,
			CountDownLatch start) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("동시 요청 시작을 기다리지 못했습니다.");
		}
		return mockMvc.perform(post("/api/v1/users/anonymous")
						.header(UserService.IDEMPOTENCY_KEY_HEADER, installationId))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
	}
}
