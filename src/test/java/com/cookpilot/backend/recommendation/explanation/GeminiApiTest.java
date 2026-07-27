package com.cookpilot.backend.recommendation.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gemini 와이어 포맷 DTO 검증. 실제로 나가는 JSON 의 필드명·중첩 구조가 맞는지,
 * 응답 파싱이 형식 이탈을 걸러내는지 본다.
 */
class GeminiApiTest {

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	private GeminiRecommendationExplanationClient client() {
		return new GeminiRecommendationExplanationClient(
				new GeminiProperties(false, "", "gemini-3.5-flash", "http://localhost",
						Duration.ofSeconds(2), Duration.ofSeconds(4)),
				objectMapper);
	}

	@Test
	void 요청_JSON이_Gemini_와이어_포맷_그대로_나간다() {
		JsonNode schema = objectMapper.readTree(
				GeminiRecommendationExplanationClient.REASONS_SCHEMA);
		GeminiApi.GenerateContentRequest request = GeminiApi.GenerateContentRequest.ofUserText(
				"프롬프트", new GeminiApi.GenerationConfig(0.2, 1024, "application/json", schema,
						new GeminiApi.ThinkingConfig(0)));

		JsonNode expected = objectMapper.readTree("""
				{
				  "contents": [{"role": "user", "parts": [{"text": "프롬프트"}]}],
				  "generationConfig": {
				    "temperature": 0.2,
				    "maxOutputTokens": 1024,
				    "responseMimeType": "application/json",
				    "responseJsonSchema": %s,
				    "thinkingConfig": {"thinkingBudget": 0}
				  }
				}
				""".formatted(GeminiRecommendationExplanationClient.REASONS_SCHEMA));

		JsonNode actual = objectMapper.valueToTree(request);
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void 모르는_필드가_섞여도_응답을_읽는다() {
		GeminiApi.GenerateContentResponse response = objectMapper.readValue("""
				{
				  "candidates": [{
				    "content": {"role": "model", "parts": [{"text": "{\\"reasons\\":[\\"한 줄\\"]}"}]},
				    "finishReason": "STOP"
				  }],
				  "usageMetadata": {"totalTokenCount": 42},
				  "modelVersion": "gemini-3.5-flash"
				}
				""", GeminiApi.GenerateContentResponse.class);

		assertThat(client().parseReasons(response)).contains(List.of("한 줄"));
	}

	@Test
	void 코드펜스로_감싸_와도_벗겨서_읽는다() {
		assertThat(client().parseReasons(responseWithText(
				"```json\n{\"reasons\":[\"한 줄\"]}\n```")))
				.contains(List.of("한 줄"));
	}

	@Test
	void 후보가_없으면_비운다() {
		assertThat(client().parseReasons(new GeminiApi.GenerateContentResponse(List.of())))
				.isEmpty();
		assertThat(client().parseReasons(null)).isEmpty();
	}

	@Test
	void 형식을_벗어난_설명은_통째로_버린다() {
		// 상한(180자) 초과
		String tooLong = "가".repeat(181);
		assertThat(client().parseReasons(responseWithText(
				"{\"reasons\":[\"%s\"]}".formatted(tooLong)))).isEmpty();
		// 줄바꿈 포함
		assertThat(client().parseReasons(responseWithText(
				"{\"reasons\":[\"두\\n줄\"]}"))).isEmpty();
		// 빈 문자열
		assertThat(client().parseReasons(responseWithText(
				"{\"reasons\":[\"\"]}"))).isEmpty();
		// reasons 키 자체가 없음
		assertThat(client().parseReasons(responseWithText("{}"))).isEmpty();
		// JSON 이 아님
		assertThat(client().parseReasons(responseWithText("설명입니다"))).isEmpty();
	}

	@Test
	void 빈_reasons_배열은_빈_목록으로_통과한다() {
		assertThat(client().parseReasons(responseWithText("{\"reasons\":[]}")))
				.contains(List.of());
	}

	@Test
	void firstText는_파트가_비면_empty다() {
		assertThat(new GeminiApi.GenerateContentResponse(List.of(
				new GeminiApi.Candidate(new GeminiApi.Content("model", List.of()))))
				.firstText()).isEqualTo(Optional.empty());
	}

	@Test
	void firstText는_배열에_null_원소가_섞여도_empty다() {
		// Jackson 은 JSON 배열의 null 을 List 의 null 원소로 역직렬화한다.
		// {"candidates":[null]} / "parts":[null] 응답이 NPE 로 새 나가면
		// fallback 대신 추천 API 전체가 500 이 된다.
		java.util.ArrayList<GeminiApi.Candidate> nullCandidate = new java.util.ArrayList<>();
		nullCandidate.add(null);
		assertThat(new GeminiApi.GenerateContentResponse(nullCandidate).firstText())
				.isEmpty();

		java.util.ArrayList<GeminiApi.Part> nullPart = new java.util.ArrayList<>();
		nullPart.add(null);
		assertThat(new GeminiApi.GenerateContentResponse(List.of(
				new GeminiApi.Candidate(new GeminiApi.Content("model", nullPart))))
				.firstText()).isEmpty();

		assertThat(new GeminiApi.GenerateContentResponse(List.of(
				new GeminiApi.Candidate(null)))
				.firstText()).isEmpty();
	}

	private GeminiApi.GenerateContentResponse responseWithText(String text) {
		return new GeminiApi.GenerateContentResponse(List.of(
				new GeminiApi.Candidate(
						new GeminiApi.Content("model", List.of(new GeminiApi.Part(text))))));
	}
}
