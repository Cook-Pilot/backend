package com.cookpilot.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
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
						.header("Authorization", "Bearer " + token))
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
		mockMvc.perform(post("/api/v1/auth/apple")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"whatever\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 제공자_설정이_없으면_500이고_설정_내용을_노출하지_않는다() throws Exception {
		// 구글 클라이언트 ID 미설정 상태. 클라이언트 잘못이 아니므로 4xx 가 아니고,
		// 어떤 키가 비었는지는 응답에 드러나면 안 된다(로그에만).
		mockMvc.perform(post("/api/v1/auth/google")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"whatever\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("GOOGLE_CLIENT_IDS"))));
	}

	@Test
	void 제공자_이름은_대소문자를_가리지_않는다() throws Exception {
		// 경로 변수는 ASCII 식별자라 Locale.ROOT 로 해석한다. 검증기가 없는 DEV 는
		// 소셜 경로로 들어오면 400 이어야 한다(개발자 로그인은 /auth/dev 전용).
		mockMvc.perform(post("/api/v1/auth/DEV")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"whatever\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 같은_신원으로_동시에_로그인해도_계정은_하나만_생긴다() throws Exception {
		// (provider, providerUserId) UNIQUE 제약에 경쟁이 붙으면 한쪽 INSERT 가 실패한다.
		// 그때 상대가 만든 행을 재조회해 같은 계정으로 수렴해야 한다(로그인은 멱등).
		int attempts = 4;
		java.util.concurrent.ExecutorService pool =
				java.util.concurrent.Executors.newFixedThreadPool(attempts);
		try {
			java.util.List<java.util.concurrent.Future<String>> results = new java.util.ArrayList<>();
			for (int i = 0; i < attempts; i++) {
				results.add(pool.submit(() -> objectMapper.readTree(loginBody()).get("userId").asText()));
			}
			java.util.Set<String> userIds = new java.util.HashSet<>();
			for (java.util.concurrent.Future<String> result : results) {
				userIds.add(result.get(20, java.util.concurrent.TimeUnit.SECONDS));
			}
			assertThat(userIds).hasSize(1);
		} finally {
			pool.shutdownNow();
		}
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
