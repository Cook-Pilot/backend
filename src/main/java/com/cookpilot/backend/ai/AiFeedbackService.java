package com.cookpilot.backend.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.recipe.Recipe;
import com.cookpilot.backend.recipe.RecipeService;
import com.cookpilot.backend.recipe.RecipeStep;

/**
 * F-08 조리 중 예외 상황 답변.
 *
 * 조리 진행은 계속 프론트가 관리한다. 서버는 레시피 존재 여부를 확인하고, 실행
 * instruction이 없는 구버전 요청만 원본 단계도 확인한다. 그 뒤 안전 규칙 → Gemini →
 * 보수적인 fallback 순서로 답한다.
 */
@Service
public class AiFeedbackService {

	private final RecipeService recipeService;
	private final SafetyRuleCoach safetyRuleCoach;
	private final AiFeedbackClient aiFeedbackClient;
	private final AiFeedbackRateLimiter rateLimiter;

	public AiFeedbackService(
			RecipeService recipeService,
			SafetyRuleCoach safetyRuleCoach,
			AiFeedbackClient aiFeedbackClient,
			AiFeedbackRateLimiter rateLimiter) {
		this.recipeService = recipeService;
		this.safetyRuleCoach = safetyRuleCoach;
		this.aiFeedbackClient = aiFeedbackClient;
		this.rateLimiter = rateLimiter;
	}

	public AiFeedbackResponse feedback(
			UUID userId,
			UUID recipeId,
			int stepIndex,
			String userSpeech,
			String instruction,
			Integer remainingSeconds) {
		Recipe recipe = recipeService.findById(recipeId);
		String currentInstruction;
		if (instruction == null) {
			// 구버전 클라이언트는 실행 스냅샷을 보내지 않으므로 원본 단계로 보완한다.
			currentInstruction = findStep(
					recipe.steps(), recipeId, stepIndex).instruction();
		} else {
			// 개인 버전은 단계 ADD/REMOVE 후 실행 순서로 다시 인덱싱된다. 따라서 프론트가
			// 보내는 현재 실행 스냅샷을 정본으로 쓰고 원본 단계의 같은 인덱스를 강제하지 않는다.
			currentInstruction = instruction.strip();
		}
		AiFeedbackContext context = new AiFeedbackContext(
				recipe.id(),
				recipe.title(),
				stepIndex,
				currentInstruction,
				remainingSeconds,
				userSpeech.strip());

		return safetyRuleCoach.answer(context)
				.map(advice -> response(advice, context, "SAFETY_RULE"))
				.orElseGet(() -> generatedOrFallback(userId, context));
	}

	private RecipeStep findStep(List<RecipeStep> steps, UUID recipeId, int stepIndex) {
		return steps.stream()
				.filter(s -> s.stepIndex() == stepIndex)
				.findFirst()
				.orElseThrow(() -> new NotFoundException(
						"레시피 " + recipeId + "에 " + stepIndex + "번 단계가 없습니다."));
	}

	private AiFeedbackResponse response(
			AiFeedbackAdvice advice,
			AiFeedbackContext context,
			String source) {
		return new AiFeedbackResponse(
				false,
				advice.speechText(),
				advice.screenText(),
				advice.suggestedAction(),
				Map.of(
						"problem", advice.problem(),
						"source", source,
						"currentStepIndex", context.stepIndex()));
	}

	/**
	 * 안전 규칙은 무료 API 쿼터와 무관하게 항상 답해야 하므로 그 다음에만 제한을 적용한다.
	 */
	private AiFeedbackResponse generatedOrFallback(
			UUID userId, AiFeedbackContext context) {
		rateLimiter.acquire(userId);
		return aiFeedbackClient.advise(context)
				.map(advice -> response(advice, context, "GEMINI"))
				.orElseGet(() -> response(fallback(context), context, "FALLBACK"));
	}

	private AiFeedbackAdvice fallback(AiFeedbackContext context) {
		String speech = context.userSpeech().toLowerCase(Locale.ROOT);

		if (containsAny(speech, "물이 안 끓", "안 끓어", "끓지 않")) {
			return new AiFeedbackAdvice(
					"뚜껑을 덮고 화력을 한 단계 높인 뒤 1분 더 기다려보세요.",
					"뚜껑을 덮고 화력을 한 단계 높여보세요. 원하면 타이머를 1분 연장할 수 있어요.",
					new AiFeedbackResponse.SuggestedAction("EXTEND_TIMER", 60),
					"WATER_NOT_BOILING");
		}
		if (containsAny(speech, "너무 짜", "짠 것", "간이 세")) {
			return new AiFeedbackAdvice(
					"물을 조금씩 추가하고 매번 잘 섞어 간을 다시 확인하세요.",
					"물이나 간하지 않은 재료를 소량씩 추가하고, 섞은 뒤 다시 맛을 확인하세요.",
					null,
					"TOO_SALTY");
		}
		if (containsAny(speech, "싱거", "간이 약", "맛이 안 나")) {
			return new AiFeedbackAdvice(
					"양념을 한 번에 많이 넣지 말고 조금씩 추가하며 간을 확인하세요.",
					"양념을 소량씩 추가하고 충분히 섞은 뒤 매번 간을 확인하세요.",
					null,
					"TOO_BLAND");
		}
		if (containsAny(speech, "재료가 없", "대신 넣", "대체 재료")) {
			return new AiFeedbackAdvice(
					"필수 재료인지 먼저 확인하고, 비슷한 맛과 역할의 재료가 없다면 생략하는 편이 안전해요.",
					"재료의 역할을 확인하세요. 확실한 대체재가 없으면 임의로 많이 넣지 말고 생략하거나 레시피를 조정하세요.",
					null,
					"MISSING_INGREDIENT");
		}

		return new AiFeedbackAdvice(
				"불을 잠시 낮추고 현재 단계의 안내와 음식 상태를 다시 확인해 주세요.",
				"열을 낮춘 뒤 현재 단계 설명과 음식의 색·냄새·익힘 상태를 확인하세요. 안전이 의심되면 먹지 마세요.",
				null,
				"OTHER");
	}

	private boolean containsAny(String value, String... needles) {
		for (String needle : needles) {
			if (value.contains(needle)) {
				return true;
			}
		}
		return false;
	}
}
