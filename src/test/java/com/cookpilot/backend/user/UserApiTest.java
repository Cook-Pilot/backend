package com.cookpilot.backend.user;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
	void 세션_토큰의_주인을_돌려준다() throws Exception {
		UUID userId = createTestUser();

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, bearerFor(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andExpect(jsonPath("$.displayName").value("테스트 사용자"));
	}

	@Test
	void 세션_토큰이_없으면_개인화_요청을_거부한다() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, ""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER_SESSION_REQUIRED"));
	}

	@Test
	void 존재하지_않는_사용자는_구조화된_오류_코드를_반환한다() throws Exception {
		// 서명은 멀쩡한데 그 사이 계정이 사라진 경우. 401(위조)과 구분돼야 한다.
		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, bearerFor(
								UUID.fromString("99999999-0000-0000-0000-000000000000"))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void 프로필을_입력하면_저장되고_물어본_시각이_찍힌다() throws Exception {
		String user = bearerFor(createTestUser());

		mockMvc.perform(patch("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, user)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"gender\": \"F\", \"ageGroup\": 20}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gender").value("F"))
				.andExpect(jsonPath("$.ageGroup").value(20))
				.andExpect(jsonPath("$.profileAskedAt").isNotEmpty());

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gender").value("F"))
				.andExpect(jsonPath("$.ageGroup").value(20))
				.andExpect(jsonPath("$.profileAskedAt").isNotEmpty());
	}

	@Test
	void 건너뛰기는_값_없이_물어본_시각만_기록한다() throws Exception {
		String user = bearerFor(createTestUser());

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profileAskedAt").isEmpty());

		mockMvc.perform(patch("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, user)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gender").isEmpty())
				.andExpect(jsonPath("$.ageGroup").isEmpty())
				.andExpect(jsonPath("$.profileAskedAt").isNotEmpty());
	}

	@Test
	void 허용되지_않은_프로필_값은_400을_반환한다() throws Exception {
		String user = bearerFor(createTestUser());

		mockMvc.perform(patch("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, user)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"gender\": \"X\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(patch("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, user)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ageGroup\": 25}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 사용자마다_즐겨찾기가_분리된다() throws Exception {
		String firstUser = bearerFor(createTestUser());
		String secondUser = bearerFor(createTestUser());
		String favoritePath =
				"/api/v1/recipes/10000000-0000-0000-0000-000000000001/favorite";

		mockMvc.perform(put(favoritePath)
						.header(HttpHeaders.AUTHORIZATION, firstUser))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/favorites")
						.header(HttpHeaders.AUTHORIZATION, firstUser))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get("/api/v1/favorites")
						.header(HttpHeaders.AUTHORIZATION, secondUser))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 사용자마다_후기와_개인_레시피가_분리된다() throws Exception {
		String firstUser = bearerFor(createTestUser());
		String secondUser = bearerFor(createTestUser());

		String reviewBody = mockMvc.perform(post("/api/v1/reviews")
						.header(HttpHeaders.AUTHORIZATION, firstUser)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "%s",
								  "rating": 5,
								  "comment": "첫 번째 사용자 후기",
								  "targetServings": 1
								}
								""".formatted(UUID.randomUUID(), TestRecipeIds.RAMEN_RECIPE_ID)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode review = objectMapper.readTree(reviewBody);
		String reviewId = review.get("id").asText();

		mockMvc.perform(get("/api/v1/recipes/"
						+ TestRecipeIds.RAMEN_RECIPE_ID + "/reviews")
						.header(HttpHeaders.AUTHORIZATION, secondUser))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/v1/reviews/" + reviewId)
						.header(HttpHeaders.AUTHORIZATION, secondUser))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/recipes/"
						+ TestRecipeIds.RAMEN_RECIPE_ID + "/personal-versions")
						.header(HttpHeaders.AUTHORIZATION, secondUser))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/v1/home/recent-recipes")
						.header(HttpHeaders.AUTHORIZATION, secondUser))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		mockMvc.perform(get("/api/v1/reviews/" + reviewId)
						.header(HttpHeaders.AUTHORIZATION, firstUser))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(reviewId));
		mockMvc.perform(get("/api/v1/home/recent-recipes")
						.header(HttpHeaders.AUTHORIZATION, firstUser))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id")
						.value(TestRecipeIds.RAMEN_RECIPE_ID.toString()));
	}
}
