package com.cookpilot.backend.recommendation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GeminiRecommendationExplanationClient
		implements RecommendationExplanationClient {

	private static final Logger log =
			LoggerFactory.getLogger(GeminiRecommendationExplanationClient.class);

	private final boolean enabled;
	private final String apiKey;
	private final String model;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public GeminiRecommendationExplanationClient(
			@Value("${cookpilot.ai.gemini.enabled:false}") boolean enabled,
			@Value("${cookpilot.ai.gemini.api-key:}") String apiKey,
			@Value("${cookpilot.ai.gemini.model:gemini-3.5-flash}") String model,
			@Value("${cookpilot.ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
			ObjectMapper objectMapper) {
		this.enabled = enabled;
		this.apiKey = apiKey;
		this.model = model;
		SimpleClientHttpRequestFactory requestFactory =
				new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(4));
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.build();
		this.objectMapper = objectMapper;
	}

	@Override
	public Optional<List<String>> explainAll(
			List<RecommendationExplanationContext> contexts) {
		if (!enabled || apiKey == null || apiKey.isBlank()) {
			return Optional.empty();
		}
		if (contexts.isEmpty()) {
			return Optional.of(List.of());
		}

		try {
			Map<String, Object> body = Map.of(
					"contents", List.of(Map.of(
							"role", "user",
							"parts", List.of(Map.of("text", prompt(contexts))))),
					"generationConfig", Map.of(
							"temperature", 0.2,
							"maxOutputTokens", 360,
							"responseMimeType", "application/json",
							"responseJsonSchema", Map.of(
									"type", "object",
									"properties", Map.of(
											"reasons", Map.of(
													"type", "array",
													"items", Map.of("type", "string"))),
									"required", List.of("reasons"))));

			String response = restClient.post()
					.uri("/v1beta/models/{model}:generateContent", model)
					.header("x-goog-api-key", apiKey)
					.body(body)
					.retrieve()
					.body(String.class);
			return parseReason(response);
		} catch (RestClientException | IllegalArgumentException exception) {
			log.warn("Gemini 추천 설명 생성에 실패해 추천 fallback을 사용합니다 ({})",
					exception.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	@Override
	public String model() {
		return model;
	}

	private Optional<List<String>> parseReason(String response) {
		if (response == null || response.isBlank()) {
			return Optional.empty();
		}
		try {
			JsonNode root = objectMapper.readTree(response);
			String generated = root.path("candidates")
					.path(0)
					.path("content")
					.path("parts")
					.path(0)
					.path("text")
					.asText("");
			if (generated.isBlank()) {
				return Optional.empty();
			}
			JsonNode payload = objectMapper.readTree(stripCodeFence(generated));
			JsonNode reasonsNode = payload.path("reasons");
			if (!reasonsNode.isArray()) {
				return Optional.empty();
			}
			List<String> reasons = new ArrayList<>();
			for (JsonNode item : reasonsNode) {
				String reason = item.asText("").trim();
				if (reason.isBlank() || reason.length() > 180 || reason.contains("\n")) {
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
