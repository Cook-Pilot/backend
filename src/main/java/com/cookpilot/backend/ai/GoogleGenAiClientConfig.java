package com.cookpilot.backend.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;

/**
 * Gemini 호출에 타임아웃을 건다. SDK 기본값이 무한이라 안 걸면 톰캣 스레드가 마른다.
 * 자동설정 빈은 ConditionalOnMissingBean 이라 이 빈이 이기고, 그쪽 키 검사도 같이 사라져 여기서 다시 한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
public class GoogleGenAiClientConfig {

	/** 프론트가 8초에 끊는다. 목 폴백이 그 안에 도착하려면 더 짧아야 한다(실측 2.9~5.2초). */
	private static final int CALL_TIMEOUT_MILLIS = 6_000;

	private static final String API_KEY_PROPERTY = "spring.ai.google.genai.api-key";

	// @Value 를 쓰면 안 된다. 이 빈은 PropertySourcesPlaceholderConfigurer 보다 먼저 만들어져
	// 플레이스홀더가 안 풀린 채로 박힌다(예외도 안 난다). Environment 는 그 순서를 안 탄다.
	@Bean
	Client googleGenAiClient(Environment environment) {
		String apiKey = environment.getProperty(API_KEY_PROPERTY);
		if (!StringUtils.hasText(apiKey) || apiKey.startsWith("${")) {
			// 안 죽이면 키 없는 Client 로 모든 호출이 조용히 목으로 떨어진다.
			throw new IllegalStateException(
					"Gemini API 키가 없습니다: " + API_KEY_PROPERTY + " = " + apiKey
							+ " (GEMINI_API_KEY 를 설정하거나 AI_CHAT_PROVIDER 를 끄세요)");
		}
		return Client.builder()
				.apiKey(apiKey)
				.httpOptions(HttpOptions.builder().timeout(CALL_TIMEOUT_MILLIS).build())
				.build();
	}
}
