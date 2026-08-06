package com.cookpilot.backend.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

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
 * 여기서 같은 타입을 먼저 등록하면 자동설정이 물러난다. 물러나면서 자동설정이 갖고 있던
 * "키가 없으면 기동에서 죽인다"는 가드도 같이 사라지므로, 그 가드를 아래에서 다시 세운다.
 *
 * <p><b>키를 {@code @Value} 로 받지 않는 이유</b> — 이 빈은 일반 빈 생성 단계보다 앞서 만들어진다.
 * spring-ai 의 {@code CachedContentServiceCondition} 이 조건 평가(= BFPP 단계) 중에
 * {@code getBean(GoogleGenAiChatModel.class)} 를 호출하고, 그 짝인 캐시 서비스 빈이 기본 ON 이라
 * 항상 밟힌다. 그 시점엔 {@code PropertySourcesPlaceholderConfigurer} 가 아직 돌지 않아
 * {@code resolveEmbeddedValue} 가 <b>예외 없이 원문을 그대로 돌려준다</b> — 즉 apiKey 에
 * {@code "${spring.ai.google.genai.api-key}"} 리터럴이 박힌 Client 가 싱글턴 캐시에 남는다.
 * {@link Environment} 는 refresh 첫 순간부터 완성돼 있어 이 순서 문제를 타지 않는다.
 *
 * <p>클래스 전체가 {@code spring.ai.model.chat=google-genai} 일 때만 산다. matchIfMissing 을 주지
 * 않는 것이 핵심이다 — 기본값(none)에서 이 빈이 만들어지면 키가 없어 기동이 죽는다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
public class GoogleGenAiClientConfig {

	/**
	 * 호출 전체(연결+응답)의 상한. OkHttp callTimeout 으로 매핑된다.
	 *
	 * <p>프론트는 8초에 요청을 끊고 "네트워크가 안 좋다"고 말한다. 서버 상한을 8초에 맞추면
	 * 목 폴백이 <b>제때 도착할 수 있는 창이 없다</b> — 8초에 끊고 목을 만들어 보내봐야 프론트는 이미
	 * 포기한 뒤다. 폴백이 의미를 가지려면 프론트 마감보다 충분히 짧아야 한다.
	 *
	 * <p>실측(연속 5회) 2.9~5.2초. 6초면 정상 응답은 통과시키고, 늦어지는 경우엔 목이 프론트
	 * 마감 안에 들어간다.
	 */
	private static final int CALL_TIMEOUT_MILLIS = 6_000;

	private static final String API_KEY_PROPERTY = "spring.ai.google.genai.api-key";

	@Bean
	Client googleGenAiClient(Environment environment) {
		String apiKey = environment.getProperty(API_KEY_PROPERTY);
		if (!StringUtils.hasText(apiKey) || apiKey.startsWith("${")) {
			// 여기서 죽이지 않으면 키가 없는 Client 가 그대로 살아서 모든 호출이 조용히 목으로 떨어진다.
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
