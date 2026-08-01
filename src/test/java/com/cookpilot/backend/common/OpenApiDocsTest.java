package com.cookpilot.backend.common;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

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

	/**
	 * 스펙을 파일로 떨군다. 단언이 아니라 산출물 생성이 목적이다.
	 * springdoc 스펙은 런타임에만 존재하는데, 프론트 클라이언트 생성과 CI 의 스펙 최신성 검사에는
	 * 파일이 필요하다. gradle 플러그인은 앱을 실제 기동해야 해서 DB(=Docker)를 끌고 오는 반면,
	 * 여기 MockMvc 컨텍스트는 기본 프로파일 + h2 라 Docker 없이 같은 스펙을 얻는다.
	 * 정본은 {@code docs/openapi.json}. 갱신 방법은 docs/feat-swagger-ui-#31.md 참고.
	 */
	@Test
	void 스펙을_파일로_덤프한다() throws Exception {
		String spec = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		// 키 정렬 + 들여쓰기로 정규화해서 쓴다. 스캔 순서가 흔들려도 바이트가 같아야
		// CI 의 diff 가 헛실패하지 않고, 커밋된 파일도 사람이 읽을 수 있다.
		ObjectMapper mapper = JsonMapper.builder()
				.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
				.enable(SerializationFeature.INDENT_OUTPUT)
				.build();

		Path out = Path.of("build", "openapi.json");
		Files.createDirectories(out.getParent());
		Files.writeString(out, mapper.writeValueAsString(mapper.readValue(spec, Object.class)) + "\n");
	}
}
