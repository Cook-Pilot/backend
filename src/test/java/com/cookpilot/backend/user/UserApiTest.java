package com.cookpilot.backend.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;

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
	void 헤더가_없으면_기존_데모_사용자를_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000001"))
				.andExpect(jsonPath("$.email").value("demo@cookpilot.app"))
				.andExpect(jsonPath("$.displayName").value("데모 사용자"))
				.andExpect(jsonPath("$.betaNumber").value(0))
				.andExpect(jsonPath("$.anonymous").value(false));
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
}
