package com.cookpilot.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;
import com.cookpilot.backend.user.UserService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 조리 진행은 프론트가 관리하므로 현재 실행 단계와 타이머 맥락을 요청 본문으로 받는다.
 */
class AiFeedbackApiTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 안전_질문은_Gemini와_무관하게_결정적인_응답을_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + TestRecipeIds.RAMEN_RECIPE_ID
								+ "\",\"stepIndex\":0,\"userSpeech\":\"닭고기가 덜 익은 것 같아\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mock").value(false))
				.andExpect(jsonPath("$.speechText").exists())
				.andExpect(jsonPath("$.screenText").exists())
				.andExpect(jsonPath("$.suggestedAction").isEmpty())
				.andExpect(jsonPath("$.eventPayload.problem").value("UNDERCOOKED_RISK"))
				.andExpect(jsonPath("$.eventPayload.source").value("SAFETY_RULE"))
				.andExpect(jsonPath("$.eventPayload.currentStepIndex").value(0))
				.andExpect(jsonPath("$.eventPayload.userSpeech").doesNotExist());
	}

	@Test
	void 개인버전_실행_instruction이_있으면_원본에_없는_단계_인덱스도_답한다()
			throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "recipeId": "%s",
								  "stepIndex": 99,
								  "userSpeech": "추가한 닭고기가 덜 익었어",
								  "instruction": "개인 버전에서 추가한 닭고기를 충분히 익힌다.",
								  "remainingSeconds": 30
								}
								""".formatted(TestRecipeIds.RAMEN_RECIPE_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventPayload.source").value("SAFETY_RULE"))
				.andExpect(jsonPath("$.eventPayload.currentStepIndex").value(99));
	}

	@Test
	void userSpeech_없으면_400() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + TestRecipeIds.RAMEN_RECIPE_ID
								+ "\",\"stepIndex\":0}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void recipeId_없으면_400() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"stepIndex\":0,\"userSpeech\":\"물이 안 끓어\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 사용자_세션_헤더가_없으면_401() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.header(UserService.USER_ID_HEADER, "")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + TestRecipeIds.RAMEN_RECIPE_ID
								+ "\",\"stepIndex\":0,\"userSpeech\":\"물이 안 끓어\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER_SESSION_REQUIRED"));
	}

	@Test
	void 없는_레시피면_404() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"99999999-0000-0000-0000-000000000000\","
								+ "\"stepIndex\":0,\"userSpeech\":\"물이 안 끓어\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 구버전_요청에서_없는_단계면_404() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipeId\":\"" + TestRecipeIds.RAMEN_RECIPE_ID
								+ "\",\"stepIndex\":99,\"userSpeech\":\"물이 안 끓어\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 음성_길이와_단계_타이머_범위를_검증한다() throws Exception {
		String tooLong = "가".repeat(501);
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"recipeId":"%s","stepIndex":0,"userSpeech":"%s"}
								""".formatted(TestRecipeIds.RAMEN_RECIPE_ID, tooLong)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"recipeId":"%s","stepIndex":-1,"userSpeech":"질문"}
								""".formatted(TestRecipeIds.RAMEN_RECIPE_ID)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "recipeId":"%s",
								  "stepIndex":0,
								  "userSpeech":"질문",
								  "remainingSeconds":-1
								}
								""".formatted(TestRecipeIds.RAMEN_RECIPE_ID)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 빈_instruction은_400() throws Exception {
		mockMvc.perform(post("/api/v1/ai-feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "recipeId":"%s",
								  "stepIndex":0,
								  "userSpeech":"질문",
								  "instruction":" "
								}
								""".formatted(TestRecipeIds.RAMEN_RECIPE_ID)))
				.andExpect(status().isBadRequest());
	}
}
