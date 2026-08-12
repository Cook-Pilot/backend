package com.cookpilot.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.user.UserService;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인과 세션 토큰 인증. 소셜 제공자 호출은 목이 아니라 실제 검증기를 쓰므로(외부 호출이 필요),
 * 여기서는 외부 의존이 없는 개발자 로그인으로 토큰 발급·검증 경로를 확인한다.
 */
@TestPropertySource(properties = "cookpilot.auth.dev-login-secret=test-dev-secret")
class AuthApiTest extends PostgresApiTestBase {

	private static final String DEV_SECRET = "test-dev-secret";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void 개발자_로그인은_토큰을_발급한다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/dev")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"secret\":\"" + DEV_SECRET + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.userId").isNotEmpty())
				.andExpect(jsonPath("$.displayName").value("개발자"));
	}

	@Test
	void 시크릿이_틀리면_401() throws Exception {
		mockMvc.perform(post("/api/v1/auth/dev")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"secret\":\"wrong\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void 발급받은_토큰으로_개인화_API를_호출할_수_있다() throws Exception {
		String token = issueDevToken();

		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", "Bearer " + token)
						// 구 헤더가 함께 와도 토큰이 우선한다.
						.header(UserService.USER_ID_HEADER, ""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("개발자"));
	}

	@Test
	void 위조된_토큰은_401() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", "Bearer not-a-real-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void Bearer가_아닌_Authorization은_401() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", "Basic abcdef"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 같은_개발자_로그인은_계정을_새로_만들지_않는다() throws Exception {
		String first = objectMapper.readTree(loginBody()).get("userId").asText();
		String second = objectMapper.readTree(loginBody()).get("userId").asText();

		assertThat(second).isEqualTo(first);
	}

	@Test
	void 지원하지_않는_제공자는_400() throws Exception {
		mockMvc.perform(post("/api/v1/auth/naver")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"whatever\"}"))
				.andExpect(status().isBadRequest());
	}

	private String issueDevToken() throws Exception {
		return objectMapper.readTree(loginBody()).get("token").asText();
	}

	private String loginBody() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/dev")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"secret\":\"" + DEV_SECRET + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}
}
