package com.cookpilot.backend.recommendation.explanation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GeminiRecommendationExplanationClient
		implements RecommendationExplanationClient {

	private static final Logger log =
			LoggerFactory.getLogger(GeminiRecommendationExplanationClient.class);

	private static final double TEMPERATURE = 0.2;
	/**
	 * 최대 유효 응답(3건 × 180자 한국어 + JSON 문법)이 잘리지 않을 크기.
	 * thinking 예산을 0 으로 꺼서(ThinkingConfig) 이 예산이 전부 본문에 쓰이게 한다.
	 */
	private static final int MAX_OUTPUT_TOKENS = 1024;
	/** 설명 한 줄의 상한. 넘거나 줄바꿈이 섞이면 모델이 형식을 벗어난 것으로 보고 버린다. */
	private static final int MAX_REASON_LENGTH = 180;

	/**
	 * 응답 형식 강제용 JSON 스키마. 도메인이 아니라 벤더에 넘기는 스키마 리터럴이라
	 * 레코드로 쪼개지 않고 JSON 그대로 둔다(생성자에서 한 번만 파싱).
	 */
	static final String REASONS_SCHEMA = """
			{
			  "type": "object",
			  "properties": {
			    "reasons": { "type": "array", "items": { "type": "string" } }
			  },
			  "required": ["reasons"]
			}
			""";

	private final GeminiProperties properties;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final GeminiApi.GenerationConfig generationConfig;

	public GeminiRecommendationExplanationClient(
			GeminiProperties properties,
			ObjectMapper objectMapper) {
		this.properties = properties;
		SimpleClientHttpRequestFactory requestFactory =
				new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		this.restClient = RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.build();
		this.objectMapper = objectMapper;
		JsonNode schema = objectMapper.readTree(REASONS_SCHEMA);
		this.generationConfig = new GeminiApi.GenerationConfig(
				TEMPERATURE, MAX_OUTPUT_TOKENS, "application/json", schema,
				new GeminiApi.ThinkingConfig(0));
	}

	@Override
	public Optional<List<String>> explainAll(
			List<RecommendationExplanationContext> contexts) {
		if (!properties.callable()) {
			return Optional.empty();
		}
		if (contexts.isEmpty()) {
			return Optional.of(List.of());
		}

		try {
			GeminiApi.GenerateContentRequest request =
					GeminiApi.GenerateContentRequest.ofUserText(prompt(contexts), generationConfig);

			GeminiApi.GenerateContentResponse response = restClient.post()
					.uri("/v1beta/models/{model}:generateContent", properties.model())
					.header("x-goog-api-key", properties.apiKey())
					.body(request)
					.retrieve()
					.body(GeminiApi.GenerateContentResponse.class);
			return parseReasons(response);
		} catch (RuntimeException exception) {
			// 호출·역직렬화·파싱 어디서 무엇이 어긋나도 추천 자체는 살아야 하므로
			// 전부 fallback 으로 흡수한다(수치는 이미 서버가 계산한 값이라 안전).
			log.warn("Gemini 추천 설명 생성에 실패해 추천 fallback을 사용합니다 ({})",
					exception.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	@Override
	public String model() {
		return properties.model();
	}

	/**
	 * 응답 본문에서 설명 목록을 꺼낸다. 형식이 조금이라도 어긋나면 empty 를 돌려주고
	 * 호출부가 규칙 기반 fallback 문구를 쓴다(추천 수치 자체는 이미 서버가 계산해 둔 값이라 안전).
	 */
	Optional<List<String>> parseReasons(GeminiApi.GenerateContentResponse response) {
		if (response == null) {
			return Optional.empty();
		}
		Optional<String> generated = response.firstText();
		if (generated.isEmpty()) {
			return Optional.empty();
		}
		try {
			GeminiApi.ReasonsPayload payload = objectMapper.readValue(
					stripCodeFence(generated.get()), GeminiApi.ReasonsPayload.class);
			if (payload.reasons() == null) {
				return Optional.empty();
			}
			List<String> reasons = new ArrayList<>(payload.reasons().size());
			for (String item : payload.reasons()) {
				String reason = item == null ? "" : item.trim();
				if (reason.isBlank()
						|| reason.length() > MAX_REASON_LENGTH
						|| reason.contains("\n")) {
					return Optional.empty();
				}
				reasons.add(reason);
			}
			return Optional.of(List.copyOf(reasons));
		} catch (RuntimeException exception) {
			log.warn("Gemini 추천 설명 응답 검증에 실패해 추천 fallback을 사용합니다 ({})",
					exception.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	private String prompt(List<RecommendationExplanationContext> contexts) {
		StringBuilder items = new StringBuilder();
		for (int index = 0; index < contexts.size(); index++) {
			RecommendationExplanationContext context = contexts.get(index);
			String evidence = context.evidence().stream()
					.map(item -> "%s(평점 %d)".formatted(
							item.recipeTitle(), item.rating()))
					.distinct()
					.limit(3)
					.reduce((left, right) -> left + ", " + right)
					.orElse("과거 조리 기록");
			items.append("""
					%d. 대상 요리=%s, 재료=%s, 기존 양=%s%s, 추천 양=%s%s, 변경률=%d%%, 근거=%s
					""".formatted(
					index + 1,
					context.targetRecipeTitle(),
					context.ingredientName(),
					context.originalAmount().toPlainString(),
					context.unit(),
					context.suggestedAmount().toPlainString(),
					context.unit(),
					context.changePercent(),
					evidence));
		}
		return """
				당신은 CookPilot의 조리 전 개인화 추천 설명기입니다.
				추천 수치와 근거는 서버가 이미 계산했으므로 절대 변경하지 마세요.
				각 항목마다 한국어 한 문장으로, 과장 없이 사용자가 선택할 수 있는 제안으로 설명하세요.
				사용자의 이름이나 내부 점수는 언급하지 마세요.
				입력 순서와 같은 순서의 reasons 배열로 답하세요.

				%s
				""".formatted(items);
	}

	private String stripCodeFence(String value) {
		String trimmed = value.trim();
		if (!trimmed.startsWith("```")) {
			return trimmed;
		}
		int firstLineEnd = trimmed.indexOf('\n');
		int fenceEnd = trimmed.lastIndexOf("```");
		if (firstLineEnd < 0 || fenceEnd <= firstLineEnd) {
			return trimmed;
		}
		return trimmed.substring(firstLineEnd + 1, fenceEnd).trim();
	}
}
