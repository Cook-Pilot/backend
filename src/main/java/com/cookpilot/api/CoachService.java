package com.cookpilot.api;

import com.cookpilot.api.ApiModels.AiFeedbackRequest;
import com.cookpilot.api.ApiModels.AiFeedbackResponse;
import org.springframework.stereotype.Service;

@Service
class CoachService {
  private final CookSessionRepository sessions;
  private final SafetyRuleCoach safetyRules;
  private final GeminiCoach gemini;

  CoachService(CookSessionRepository sessions, SafetyRuleCoach safetyRules, GeminiCoach gemini) {
    this.sessions = sessions;
    this.safetyRules = safetyRules;
    this.gemini = gemini;
  }

  AiFeedbackResponse answer(InstallPrincipal principal, AiFeedbackRequest request) {
    sessions.ensureActive(principal, request.cookSessionId());
    SessionContext context =
        sessions.findContext(
            principal, request.cookSessionId(), request.stepIndex(), request.remainingSeconds());
    AiFeedbackResponse response =
        safetyRules
            .safetyAnswer(request.userSpeech())
            .or(() -> gemini.answer(context, request.userSpeech()))
            .orElseGet(() -> safetyRules.fallbackAnswer(request.userSpeech(), context));
    String model = response.offlineFallback() ? "local-safety-rules" : gemini.model();
    sessions.recordAiInteraction(request.cookSessionId(), context, model, response);
    return response;
  }

  boolean isAiConfigured() {
    return gemini.isConfigured();
  }
}
