package com.cookpilot.backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;

/**
 * Gemini 호출에 타임아웃을 건다.
 *
 * <p><b>왜 직접 만드는가</b> — google-genai SDK 는 타임아웃을 <i>명시적으로 무한대로 끈다</i>
 * (ApiClient: {@code connectTimeout(0) / readTimeout(0) / writeTimeout(0)}, httpOptions.timeout 이
 * 있을 때만 callTimeout 을 건다). 그리고 Spring AI 의 {@code spring.ai.google.genai.*} 에는
 * 타임아웃 프로퍼티가 없다. 그대로 두면 Gemini 가 응답하지 않을 때 서블릿 스레드가 영구 점유돼
 * 톰캣 스레드풀이 마른다 — 조리 중에 답이 안 오는 것보다 나쁜 결과다.
 *
 * <p>Spring AI 의 {@code googleGenAiClient} 빈은 {@code @ConditionalOnMissingBean} 이라
 * 여기서 같은 타입을 먼저 등록하면 자동설정이 물러난다.
 *
 * <p>클래스 전체가 {@code spring.ai.model.chat=google-genai} 일 때만 산다. matchIfMissing 을 주지
 * 않는 것이 핵심이다 — 기본값(none)에서 이 빈이 만들어지면 키가 없어 기동이 죽는다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
public class GoogleGenAiClientConfig {

	/**
	 * 호출 전체(연결+응답)의 상한. 프론트가 8초에 요청을 끊으므로 서버가 그보다 오래 기다릴 이유가
	 * 없다 — 기다리느니 목 응답으로 떨어지는 편이 낫다. OkHttp callTimeout 으로 매핑된다.
	 */
	private static final int CALL_TIMEOUT_MILLIS = 8_000;

	@Bean
	Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey) {
		return Client.builder()
				.apiKey(apiKey)
				.httpOptions(HttpOptions.builder().timeout(CALL_TIMEOUT_MILLIS).build())
				.build();
	}
}
