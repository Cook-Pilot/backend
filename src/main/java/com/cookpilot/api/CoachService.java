package com.cookpilot.api;

import com.cookpilot.api.ApiModels.AiFeedbackRequest;
import com.cookpilot.api.ApiModels.AiFeedbackResponse;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 조리 중 음성 질문에 답한다.
 *
 * <p>기본 경로는 "판단이 필요한 질문은 전부 LLM에 던진다"이다. 룰베이스 안전 규칙은 삭제하지 않고 두 자리에 남겨 두었다.
 *
 * <ol>
 *   <li>LLM 호출 <b>앞</b>의 안전 인터셉트 — {@code cookpilot.ai.safety-intercept-enabled}로 켠다. 기본값은
 *       꺼짐. 2026-08-04 회의에서 "알레르기 같은 건 위험하니까 LLM 던지지 말자"는 전제를 실제로 검증하기로
 *       했기 때문에, 비교 측정이 가능하도록 런타임 스위치로 남겼다.
 *   <li>LLM 호출 <b>뒤</b>의 fallback — 항상 켜져 있다. Gemini가 미설정이거나 실패했을 때 답할 무언가는
 *       LLM 의존도가 높아질수록 오히려 더 필요하다.
 * </ol>
 */
@Service
class CoachService {
  private final CookSessionRepository sessions;
  private final SafetyRuleCoach safetyRules;
  private final GeminiCoach gemini;
  private final boolean safetyInterceptEnabled;

  CoachService(
      CookSessionRepository sessions,
      SafetyRuleCoach safetyRules,
      GeminiCoach gemini,
      @Value("${cookpilot.ai.safety-intercept-enabled:false}") boolean safetyInterceptEnabled) {
    this.sessions = sessions;
    this.safetyRules = safetyRules;
    this.gemini = gemini;
    this.safetyInterceptEnabled = safetyInterceptEnabled;
  }

  AiFeedbackResponse answer(InstallPrincipal principal, AiFeedbackRequest request) {
    sessions.ensureActive(principal, request.cookSessionId());
    SessionContext context =
        sessions.findContext(
            principal, request.cookSessionId(), request.stepIndex(), request.remainingSeconds());

    if (safetyInterceptEnabled) {
      Optional<AiFeedbackResponse> intercepted = safetyRules.safetyAnswer(request.userSpeech());
      if (intercepted.isPresent()) {
        return record(request, context, CoachRoute.SAFETY_INTERCEPT, intercepted.get());
      }
    }

    Optional<AiFeedbackResponse> remote = gemini.answer(context, request.userSpeech());
    if (remote.isPresent()) {
      return record(request, context, CoachRoute.REMOTE, remote.get());
    }

    // 여기부터는 Gemini가 미설정이거나 실패한 경우다. 안전 규칙을 먼저 보고, 걸리지 않으면 일반 fallback으로 답한다.
    Optional<AiFeedbackResponse> localSafety = safetyRules.safetyAnswer(request.userSpeech());
    if (localSafety.isPresent()) {
      return record(request, context, CoachRoute.SAFETY_FALLBACK, localSafety.get());
    }
    return record(
        request,
        context,
        CoachRoute.RULE_FALLBACK,
        safetyRules.fallbackAnswer(request.userSpeech(), context));
  }

  boolean isAiConfigured() {
    return gemini.isConfigured();
  }

  private AiFeedbackResponse record(
      AiFeedbackRequest request,
      SessionContext context,
      CoachRoute route,
      AiFeedbackResponse response) {
    String model = route == CoachRoute.REMOTE ? gemini.model() : route.label();
    sessions.recordAiInteraction(request.cookSessionId(), context, model, response);
    return response;
  }

  /**
   * 어느 경로가 답했는지. `ai_interactions.model`에 그대로 남는다. 이전에는 룰베이스가 답한 세 경우가 모두
   * `local-safety-rules` 하나로 뭉뚱그려져, 안전 인터셉트와 Gemini 실패를 사후에 구분할 수 없었다.
   */
  private enum CoachRoute {
    SAFETY_INTERCEPT("local-safety-intercept"),
    REMOTE("remote"),
    SAFETY_FALLBACK("local-safety-fallback"),
    RULE_FALLBACK("local-rule-fallback");

    private final String label;

    CoachRoute(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }
}
