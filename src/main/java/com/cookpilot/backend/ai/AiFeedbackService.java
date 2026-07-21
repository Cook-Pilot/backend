package com.cookpilot.backend.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.recipe.Recipe;
import com.cookpilot.backend.recipe.RecipeService;
import com.cookpilot.backend.recipe.RecipeStep;

/**
 * AI 파트 미확정. 실제 STT/LLM 연동 없이 docs/06 §9 응답 구조의 목데이터만 반환한다.
 *
 * 조리 진행은 프론트가 관리하므로 서버에 세션이 없다. 현재 단계(stepIndex)는 요청으로 받는다.
 * 이벤트 로그도 서버에 남기지 않는다(프론트 로컬 기록).
 * TODO(AI 확정 후): LLM 호출, 컨텍스트(payload) 구성, 안전 원칙(docs/06 §11) 반영.
 */
@Service
public class AiFeedbackService {

	private final RecipeService recipeService;

	public AiFeedbackService(RecipeService recipeService) {
		this.recipeService = recipeService;
	}

	public AiFeedbackResponse feedback(UUID recipeId, int stepIndex, String userSpeech) {
		Recipe recipe = recipeService.findById(recipeId);
		RecipeStep step = findStep(recipe.steps(), recipeId, stepIndex);

		return new AiFeedbackResponse(
				true,
				"아직 끓지 않으면 1분 더 끓이고, 기포가 올라오면 다음 단계로 넘어가세요.",
				"1분 더 끓인 뒤 기포가 올라오면 다음 단계로 이동하세요.",
				new AiFeedbackResponse.SuggestedAction("EXTEND_TIMER", 60),
				Map.of(
						"problem", "mock_response",
						"note", "AI 파트 미확정 - 고정 목데이터 응답",
						"currentStepIndex", step.stepIndex(),
						"userSpeech", userSpeech
				)
		);
	}

	private RecipeStep findStep(List<RecipeStep> steps, UUID recipeId, int stepIndex) {
		return steps.stream()
				.filter(s -> s.stepIndex() == stepIndex)
				.findFirst()
				.orElseThrow(() -> new NotFoundException(
						"레시피 " + recipeId + "에 " + stepIndex + "번 단계가 없습니다."));
	}
}
