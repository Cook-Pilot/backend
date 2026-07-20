package com.cookpilot.backend.ai;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.cooksession.CookSession;
import com.cookpilot.backend.cooksession.CookSessionService;
import com.cookpilot.backend.recipe.RecipeStep;

/**
 * AI 파트 미확정. 실제 STT/LLM 연동 없이 docs/06 §9 응답 구조의 목데이터만 반환한다.
 * TODO(AI 확정 후): LLM 호출, 컨텍스트(payload) 구성, 안전 원칙(docs/06 §11) 반영.
 */
@Service
public class AiFeedbackService {

	private final CookSessionService cookSessionService;

	public AiFeedbackService(CookSessionService cookSessionService) {
		this.cookSessionService = cookSessionService;
	}

	public AiFeedbackResponse feedback(UUID sessionId, String userSpeech) {
		CookSession session = cookSessionService.findById(sessionId);
		RecipeStep step = session.currentStep();

		cookSessionService.addEvent(sessionId, "AI_FEEDBACK_REQUESTED", step.stepIndex(), "VOICE",
				Map.of("userSpeech", userSpeech, "mock", true));

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
}
