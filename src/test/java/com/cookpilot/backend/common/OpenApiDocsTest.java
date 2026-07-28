package com.cookpilot.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * springdoc 배선 스모크 테스트. OpenAPI 스펙 생성과 Swagger UI 진입점이 살아 있는지만 본다.
 * 스펙 내용(개별 엔드포인트 스키마)은 단언하지 않는다 — 컨트롤러가 늘 때마다 깨지는 단언은
 * 회귀를 못 잡고 유지비만 든다. 기본 프로파일 + 테스트 h2 컨텍스트라 Docker 없이 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void openapi_스펙이_생성된다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").exists())
				.andExpect(jsonPath("$.info.title").value("CookPilot Backend API"))
				// 컨트롤러 스캔이 실제로 닿았는지 — 경로가 하나라도 잡혀야 한다.
				.andExpect(jsonPath("$.paths").isNotEmpty());
	}

	@Test
	void swagger_ui_진입점이_리다이렉트된다() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/swagger-ui/index.html"));
	}
}
