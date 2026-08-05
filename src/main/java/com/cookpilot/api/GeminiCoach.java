package com.cookpilot.api;

import static com.cookpilot.api.ApiModels.AiFeedbackResponse;
import static com.cookpilot.api.ApiModels.SuggestedAction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class GeminiCoach {
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String apiKey;
  private final String model;

  GeminiCoach(
      ObjectMapper objectMapper,
      @Value("${cookpilot.ai.gemini-api-key:}") String apiKey,
      @Value("${cookpilot.ai.gemini-model:gemini-2.5-flash}") String model) {
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.model = model;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  boolean isConfigured() {
    return apiKey != null && !apiKey.isBlank();
  }

  String model() {
    return isConfigured() ? model : "local-safety-rules";
  }

  Optional<AiFeedbackResponse> answer(SessionContext context, String speech) {
    if (!isConfigured()) {
      return Optional.empty();
    }
    try {
      String prompt = buildPrompt(context, speech);
      Map<String, Object> body =
          Map.of(
              "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
              "generationConfig",
                  Map.of("responseMimeType", "application/json", "temperature", 0.2, "maxOutputTokens", 220));
      String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
      String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
      URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + encodedModel + ":generateContent?key=" + encodedKey);
      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(10))
              .header("content-type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return Optional.empty();
      }
      JsonNode envelope = objectMapper.readTree(response.body());
      String raw = envelope.at("/candidates/0/content/parts/0/text").asText("");
      if (raw.isBlank()) {
        return Optional.empty();
      }
      JsonNode json = objectMapper.readTree(stripFence(raw));
      String speechText = json.path("speechText").asText("").trim();
      String screenText = json.path("screenText").asText(speechText).trim();
      if (speechText.isBlank() || screenText.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(new AiFeedbackResponse(speechText, screenText, parseAction(json.path("suggestedAction")), false));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (Exception exception) {
      return Optional.empty();
    }
  }

  private String buildPrompt(SessionContext context, String speech) {
    return """
        당신은 요리 초보자를 돕는 CookPilot 코치다.
        반드시 한국어 JSON 한 개만 반환한다.
        형식: {"speechText":"1~2문장","screenText":"1~2문장","suggestedAction":{"type":"EXTEND_TIMER","seconds":60}}
        suggestedAction은 비안전 조리의 타이머 연장이 꼭 필요할 때만 포함하고 seconds는 30 또는 60만 사용한다.
        suggestedAction은 사용자가 화면에서 다시 확인하기 전에는 적용되지 않는다.
        한 번에 하나의 구체적인 다음 행동만 안내한다.

        안전 규칙. 아래에 해당하면 suggestedAction을 절대 만들지 말고 보수적으로 안내한다.
        - 기름에 불이 붙었으면 물을 붓지 말라고 먼저 말하고, 불을 끄고 뚜껑으로 덮으라고 안내한다.
        - 재료 변질이나 곰팡이가 의심되면 맛보지 말고 버리라고 안내한다.
        - 알레르기가 의심되면 해당 재료와 조리도구 사용을 멈추고 성분표를 확인하라고 안내한다.
        - 육류가 덜 익은 것 같으면 먹지 말고 식품용 온도계로 중심 온도를 확인하라고 안내한다.
          색이나 조리 시간만으로 익힘을 판단하지 말라고 함께 말한다.
        - 판단이 서지 않으면 조리를 계속하라고 하지 말고 안전한 쪽을 고른다.

        레시피: %s
        현재 단계 %d: %s
        남은 시간: %s초
        사용자 발화: %s
        """
        .formatted(
            context.recipeTitle(),
            context.stepIndex() + 1,
            context.instruction(),
            context.remainingSeconds() == null ? "없음" : context.remainingSeconds(),
            speech);
  }

  private SuggestedAction parseAction(JsonNode node) {
    if (!node.isObject() || !"EXTEND_TIMER".equals(node.path("type").asText())) {
      return null;
    }
    int seconds = node.path("seconds").asInt(0);
    if (seconds != 30 && seconds != 60) {
      return null;
    }
    return new SuggestedAction("EXTEND_TIMER", seconds);
  }

  private String stripFence(String value) {
    return value.replace("```json", "").replace("```", "").trim();
  }
}
