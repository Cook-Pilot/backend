package com.cookpilot.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** F-08 Spring AI 배선이 범위 밖 모델 자동설정을 만들지 않는지 확인한다. */
@SpringBootTest
class AiFeedbackWiringTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void F8_advisor는_등록하고_자동_chat_embedding_model은_만들지_않는다() {
		assertThat(context.getBean(AiFeedbackSafetyAdvisor.class)).isNotNull();
		assertThat(context.getBean(AiFeedbackFallbackAdvisor.class)).isNotNull();
		assertThat(context.getBeansOfType(ChatModel.class)).isEmpty();
		assertThat(context.getBeansOfType(EmbeddingModel.class)).isEmpty();
	}
}
