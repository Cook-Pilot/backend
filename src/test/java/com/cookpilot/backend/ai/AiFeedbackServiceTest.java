package com.cookpilot.backend.ai;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.cookpilot.backend.recipe.Recipe;
import com.cookpilot.backend.recipe.RecipeService;
import com.cookpilot.backend.recipe.RecipeStep;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiFeedbackServiceTest {

	private static final UUID RECIPE_ID = UUID.fromString(
			"10000000-0000-0000-0000-000000000099");

	@Test
	void 안전_규칙에_걸리면_Gemini를_호출하지_않는다() {
		AtomicInteger clientCalls = new AtomicInteger();
		AiFeedbackClient client = context -> {
			clientCalls.incrementAndGet();
			return Optional.empty();
		};
		AiFeedbackService service = service(client, recipe());

		AiFeedbackResponse response = service.feedback(
				UUID.randomUUID(),
				RECIPE_ID, 0, "닭고기가 덜 익었어", null, 20);

		assertThat(clientCalls).hasValue(0);
		assertThat(response.mock()).isFalse();
		assertThat(response.eventPayload())
				.containsEntry("source", "SAFETY_RULE")
				.doesNotContainKey("userSpeech");
		assertThat(response.suggestedAction()).isNull();
	}

	@Test
	void 익지_않은_닭고기_보고는_결정적인_안전_응답을_반환한다() {
		AiFeedbackService service = service(context -> Optional.empty(), recipe());

		AiFeedbackResponse response = service.feedback(
				UUID.randomUUID(),
				RECIPE_ID, 0, "닭고기가 익지 않았어요", null, 20);

		assertThat(response.eventPayload())
				.containsEntry("source", "SAFETY_RULE")
				.containsEntry("problem", "UNDERCOOKED_RISK");
		assertThat(response.screenText()).contains("추가 가열");
	}

	@Test
	void 부정된_알레르기_언급은_짠맛_fallback을_가로채지_않는다() {
		AiFeedbackService service = service(context -> Optional.empty(), recipe());

		AiFeedbackResponse response = service.feedback(
				UUID.randomUUID(),
				RECIPE_ID, 0, "알레르기는 없어요, 소스가 너무 짜요", null, 20);

		assertThat(response.eventPayload())
				.containsEntry("source", "FALLBACK")
				.containsEntry("problem", "TOO_SALTY");
	}

	@Test
	void 안전_규칙은_사용자_Gemini_호출_한도가_소진돼도_응답한다() {
		RecipeService recipeService = mock(RecipeService.class);
		when(recipeService.findById(RECIPE_ID)).thenReturn(recipe());
		AiFeedbackRateLimiter limiter = new AiFeedbackRateLimiter(
				new AiFeedbackRateLimitProperties(1),
				Clock.systemUTC());
		AiFeedbackService service = new AiFeedbackService(
				recipeService,
				safetyAdvisor(),
				context -> Optional.empty(),
				limiter);
		UUID userId = UUID.randomUUID();

		service.feedback(
				userId, RECIPE_ID, 0, "물이 안 끓어요", null, 10);
		AiFeedbackResponse safety = service.feedback(
				userId, RECIPE_ID, 0, "불이 났어요", null, 10);

		assertThat(safety.eventPayload())
				.containsEntry("source", "SAFETY_RULE")
				.containsEntry("problem", "FIRE_RISK");
	}

	@Test
	void Gemini가_비어_있으면_결정적인_fallback을_반환한다() {
		AiFeedbackService service = service(context -> Optional.empty(), recipe());

		AiFeedbackResponse response = service.feedback(
				UUID.randomUUID(),
				RECIPE_ID, 0, "물이 안 끓어요", null, 10);

		assertThat(response.eventPayload())
				.containsEntry("source", "FALLBACK")
				.containsEntry("problem", "WATER_NOT_BOILING")
				.doesNotContainKey("userSpeech");
		assertThat(response.suggestedAction())
				.isEqualTo(new AiFeedbackResponse.SuggestedAction("EXTEND_TIMER", 60));
	}

	@Test
	void Gemini가_안전_위험으로_분류하면_모델_문구와_행동을_서버_안내로_교체한다() {
		AiFeedbackService service = service(
				context -> Optional.of(new AiFeedbackAdvice(
						"그냥 드셔도 됩니다.",
						"다음 단계로 이동하세요.",
						new AiFeedbackResponse.SuggestedAction("EXTEND_TIMER", 60),
						"UNDERCOOKED")),
				recipe());

		AiFeedbackResponse response = service.feedback(
				UUID.randomUUID(),
				RECIPE_ID,
				0,
				"색이 평소와 조금 달라요",
				null,
				10);

		assertThat(response.eventPayload())
				.containsEntry("source", "SAFETY_RULE")
				.containsEntry("problem", "UNDERCOOKED_RISK");
		assertThat(response.screenText()).contains("추가 가열");
		assertThat(response.screenText()).doesNotContain("다음 단계");
		assertThat(response.suggestedAction()).isNull();
	}

	@Test
	void 실행_instruction이_있으면_개인버전의_재인덱싱된_단계를_정본으로_쓴다() {
		AtomicReference<AiFeedbackContext> received = new AtomicReference<>();
		AiFeedbackClient client = context -> {
			received.set(context);
			return Optional.of(new AiFeedbackAdvice(
					"답변", "화면 답변", null, "OTHER"));
		};
		AiFeedbackService service = service(client, recipe());

		AiFeedbackResponse response = service.feedback(
				UUID.randomUUID(),
				RECIPE_ID,
				99,
				"어떻게 해?",
				"개인 버전에서 새로 추가한 단계",
				30);

		assertThat(response.eventPayload())
				.containsEntry("source", "GEMINI")
				.containsEntry("currentStepIndex", 99);
		assertThat(received.get().stepIndex()).isEqualTo(99);
		assertThat(received.get().instruction())
				.isEqualTo("개인 버전에서 새로 추가한 단계");
	}

	private AiFeedbackService service(AiFeedbackClient client, Recipe recipe) {
		RecipeService recipeService = mock(RecipeService.class);
		when(recipeService.findById(RECIPE_ID)).thenReturn(recipe);
		return new AiFeedbackService(
				recipeService,
				safetyAdvisor(),
				client,
				new AiFeedbackRateLimiter(
						new AiFeedbackRateLimitProperties(20),
						Clock.systemUTC()));
	}

	private AiFeedbackSafetyAdvisor safetyAdvisor() {
		return new AiFeedbackSafetyAdvisor(
				new SafetyRuleCoach(), JsonMapper.builder().build());
	}

	private Recipe recipe() {
		return new Recipe(
				RECIPE_ID,
				"테스트 요리",
				"설명",
				1.0,
				null,
				List.of(),
				List.of(new RecipeStep(
						UUID.randomUUID(),
						0,
						"원본 첫 단계",
						60,
						null,
						null)));
	}
}
