package com.cookpilot.backend.ai;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.recipe.Recipe;
import com.cookpilot.backend.recipe.RecipeService;
import com.cookpilot.backend.recipe.RecipeStep;

/**
 * 조리 중 AI 피드백. {@link CookingCoachClient} 가 호출 가능하면 LLM 응답(mock=false), 아니면
 * 고정 목데이터(mock=true)를 돌려준다. 키가 없는 CI·로컬 기본값은 항상 목이다.
 *
 * 조리 진행은 프론트가 관리하므로 서버에 세션이 없다. 현재 단계(stepIndex)는 요청으로 받는다.
 * 이벤트 로그도 서버에 남기지 않는다(프론트 로컬 기록).
 *
 * userSpeech 는 <b>클라이언트가 STT 로 변환해 보낸 문자열</b>이라는 전제다 — 서버는 음성을 받지 않는다.
 * 음성이 서버로 직접 들어오게 되면(오디오 업로드 + 서버 STT) 이 경로 전체가 컨트롤러 DTO부터 바뀐다.
 * TODO(AI 확정 후): STT/TTS 경계 확정, 타이머·최근 이벤트 컨텍스트 전달.
 */
@Service
public class AiFeedbackService {

	private static final String MOCK_SPEECH =
			"아직 끓지 않으면 1분 더 끓이고, 기포가 올라오면 다음 단계로 넘어가세요.";

	private final RecipeService recipeService;
	private final CookingCoachClient coachClient;

	public AiFeedbackService(RecipeService recipeService, CookingCoachClient coachClient) {
		this.recipeService = recipeService;
		this.coachClient = coachClient;
	}

	public AiFeedbackResponse feedback(UUID recipeId, int stepIndex, String userSpeech) {
		Recipe recipe = recipeService.findById(recipeId);
		RecipeStep step = findStep(recipe.steps(), recipeId, stepIndex);

		return coachClient.advise(recipe.title(), step, userSpeech)
				.map(speech -> new AiFeedbackResponse(false, speech))
				.orElseGet(() -> new AiFeedbackResponse(true, MOCK_SPEECH));
	}

	private RecipeStep findStep(List<RecipeStep> steps, UUID recipeId, int stepIndex) {
		return steps.stream()
				.filter(s -> s.stepIndex() == stepIndex)
				.findFirst()
				.orElseThrow(() -> new NotFoundException(
						"레시피 " + recipeId + "에 " + stepIndex + "번 단계가 없습니다."));
	}
}
