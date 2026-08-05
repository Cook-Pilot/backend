package com.cookpilot.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 를 <b>켠</b> 설정에서 컨텍스트가 뜨는지 본다. 평소 실행/테스트는 spring.ai.model.chat=none 이라
 * 이 경로가 아예 안 밟히고, 자동설정이 깨져도 배포 직전까지 모른다 — 그 구멍을 막는 스모크다.
 *
 * 더미 키를 쓴다. Spring AI 는 기동 시 키를 검증하지 않고 첫 호출에서야 쓰므로 네트워크를 타지 않는다.
 * (키가 유효한지는 여기서 확인할 수 없다 — 실제 호출은 운영에서만 일어난다.)
 */
@SpringBootTest(properties = {
		"spring.ai.model.chat=google-genai",
		"spring.ai.google.genai.api-key=dummy-key-for-wiring-test"
})
class AiFeedbackWiringTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void AI를_켜면_ChatModel과_코치가_배선된다() {
		assertThat(context.getBean(ChatModel.class)).isNotNull();
		assertThat(context.getBean(CookingCoachClient.class)).isNotNull();
		assertThat(context.getBean(AiFeedbackService.class)).isNotNull();
	}

	/**
	 * 타임아웃은 우리가 만든 Client 빈에만 걸려 있다. Spring AI 자동설정 쪽 빈이 이기면
	 * 타임아웃이 <b>조용히</b> 사라지고(호출은 그대로 되므로) 운영에서 스레드가 마를 때까지 모른다.
	 * 그래서 "누가 만든 빈인가"를 직접 본다.
	 */
	@Test
	void Gemini_Client는_타임아웃을_건_우리_빈이다() {
		String factory = ((ConfigurableApplicationContext) context).getBeanFactory()
				.getBeanDefinition("googleGenAiClient")
				.getFactoryBeanName();

		assertThat(factory).containsIgnoringCase(GoogleGenAiClientConfig.class.getSimpleName());
	}
}
