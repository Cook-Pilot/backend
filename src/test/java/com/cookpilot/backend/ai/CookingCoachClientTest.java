package com.cookpilot.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import com.cookpilot.backend.recipe.RecipeStep;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 가 꺼져 있을 때 호출을 시도하지 않는지만 본다(실제 LLM 호출은 하지 않는다).
 */
class CookingCoachClientTest {

	/** 설정이 꺼져 있으면 ChatModel 빈이 없다 — 그 상태를 그대로 흉내낸다. */
	private final CookingCoachClient client = new CookingCoachClient(new NoChatModel());

	@Test
	void AI가_꺼져_있으면_조언을_만들지_않는다() {
		RecipeStep step = new RecipeStep(null, 0, "물을 끓인다", 180, null, null);

		assertThat(client.advise("라면", step, "물이 안 끓어")).isEmpty();
	}

	/** ObjectProvider 는 인터페이스라 "빈 없음"을 표현하려면 기본 구현이 필요하다(getIfAvailable=null). */
	private static final class NoChatModel implements ObjectProvider<ChatModel> {

		@Override
		public ChatModel getObject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChatModel getObject(Object... args) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChatModel getIfAvailable() {
			return null;
		}

		@Override
		public ChatModel getIfUnique() {
			return null;
		}
	}
}
