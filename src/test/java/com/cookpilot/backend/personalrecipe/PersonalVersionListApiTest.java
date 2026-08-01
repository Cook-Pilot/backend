package com.cookpilot.backend.personalrecipe;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 개인 버전 목록 조회(GET /recipes/{recipeId}/personal-versions).
 *
 * 목록은 최신 버전부터 최대 5개다(`PersonalRecipeService.findByRecipe` 의 limit).
 * 버전 트리는 append-only 라 무한히 쌓이므로 목록 화면이 전부를 받을 이유가 없다.
 *
 * 컨테이너·DB 를 다른 테스트 클래스와 공유하므로 된장찌개를 이 클래스 전용 픽스처로 쓴다
 * (라면 = RecipeEditPipelineApiTest, 김치볶음밥 = ReviewFlowApiTest,
 * 두부조림 = RecentRecipeApiTest — 거기서 hasPersonalVersion=false 를 단언하므로 침범 금지).
 */
class PersonalVersionListApiTest extends PostgresApiTestBase {

	private static final UUID STEW_ID = TestRecipeIds.DOENJANG_STEW_RECIPE_ID;
	// V3 시드: 된장찌개 1단계, 원본 타이머 300초
	private static final UUID STEP_CHOP = UUID.fromString("30000000-0000-0000-0000-000000000401");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void 목록은_최신_버전부터_5개까지만_돌려준다() throws Exception {
		// 타이머만 매번 다르게 고쳐 6개를 쌓는다 — 단계 수정은 인분과 무관하다.
		List<String> createdIds = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			createdIds.add(createVersion(400 + i).get("id").asString());
		}

		JsonNode list = getVersions();

		assertThat(list).hasSize(5);

		// 방금 만든 6개 중 뒤의 5개가, 최신부터 역순으로 온다.
		List<String> returnedIds = list.valueStream().map(node -> node.get("id").asString()).toList();
		assertThat(returnedIds)
				.containsExactlyElementsOf(createdIds.subList(1, 6).reversed());

		List<Integer> versionNumbers =
				list.valueStream().map(node -> node.get("versionNumber").asInt()).toList();
		assertThat(versionNumbers).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
	}

	/** 조리 기록 → 그 리뷰로 버전 생성. 단계 타이머만 바꾸므로 인분 없이도 통과한다. */
	private JsonNode createVersion(int timerSeconds) throws Exception {
		UUID reviewId = submitReview();
		String response = mockMvc.perform(
						post("/api/v1/reviews/" + reviewId + "/personal-versions")
								.contentType(MediaType.APPLICATION_JSON)
								.content("""
										{
										  "setup": {
										    "ingredientAdjustments": [],
										    "stepAdjustments": [
										      {"originalStepId": "%s", "type": "MODIFY",
										       "sortOrder": 0, "timerSeconds": %d}
										    ]
										  }
										}
										""".formatted(STEP_CHOP, timerSeconds)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return objectMapper.readTree(response);
	}

	private UUID submitReview() throws Exception {
		String response = mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "%s",
								  "cookedAt": "2026-07-30T02:00:00Z",
								  "rating": 4
								}
								""".formatted(UUID.randomUUID(), STEW_ID)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return UUID.fromString(objectMapper.readTree(response).get("id").asString());
	}

	private JsonNode getVersions() throws Exception {
		String response = mockMvc.perform(get("/api/v1/recipes/" + STEW_ID + "/personal-versions"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return objectMapper.readTree(response);
	}
}
