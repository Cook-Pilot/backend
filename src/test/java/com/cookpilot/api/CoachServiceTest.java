package com.cookpilot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cookpilot.api.ApiModels.AiFeedbackRequest;
import com.cookpilot.api.ApiModels.AiFeedbackResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CoachServiceTest {
  private static final String OIL_FIRE = "기름에 불이 붙었어";
  private static final AiFeedbackResponse REMOTE_ANSWER =
      new AiFeedbackResponse("원격 답변", "원격 답변", null, false);

  private final CookSessionRepository sessions = mock(CookSessionRepository.class);
  private final GeminiCoach gemini = mock(GeminiCoach.class);
  private final SafetyRuleCoach safetyRules = new SafetyRuleCoach();

  private final UUID sessionId = UUID.randomUUID();
  private final InstallPrincipal principal = new InstallPrincipal(UUID.randomUUID(), null);

  @BeforeEach
  void stubSession() {
    when(sessions.findContext(any(), any(), anyInt(), any()))
        .thenReturn(
            new SessionContext(sessionId, "토마토 파스타", UUID.randomUUID(), 0, "면을 삶는다", 300, 120));
    when(gemini.model()).thenReturn("gemini-2.5-flash");
  }

  @Test
  void safetyQuestionGoesToTheModelWhenInterceptIsDisabled() {
    when(gemini.answer(any(), eq(OIL_FIRE))).thenReturn(Optional.of(REMOTE_ANSWER));
    CoachService coach = coachService(false);

    var response = coach.answer(principal, request(OIL_FIRE));

    assertThat(response).isEqualTo(REMOTE_ANSWER);
    verify(gemini).answer(any(), eq(OIL_FIRE));
    assertThat(recordedModel()).isEqualTo("gemini-2.5-flash");
  }

  @Test
  void safetyQuestionIsInterceptedBeforeTheModelWhenEnabled() {
    CoachService coach = coachService(true);

    var response = coach.answer(principal, request(OIL_FIRE));

    assertThat(response.screenText()).contains("뚜껑");
    verify(gemini, never()).answer(any(), any());
    assertThat(recordedModel()).isEqualTo("local-safety-intercept");
  }

  /** 인터셉트를 꺼도 Gemini가 실패하면 안전 규칙이 답해야 한다. 이 경로는 플래그와 무관하게 항상 살아 있다. */
  @Test
  void safetyRulesStillAnswerWhenTheModelFails() {
    when(gemini.answer(any(), any())).thenReturn(Optional.empty());
    CoachService coach = coachService(false);

    var response = coach.answer(principal, request(OIL_FIRE));

    assertThat(response.screenText()).contains("뚜껑");
    assertThat(response.offlineFallback()).isTrue();
    assertThat(recordedModel()).isEqualTo("local-safety-fallback");
  }

  @Test
  void nonSafetyQuestionFallsBackToRulesWhenTheModelFails() {
    when(gemini.answer(any(), any())).thenReturn(Optional.empty());
    CoachService coach = coachService(false);

    var response = coach.answer(principal, request("국물이 좀 짜"));

    assertThat(response.screenText()).contains("한 스푼씩");
    assertThat(recordedModel()).isEqualTo("local-rule-fallback");
  }

  /** 네 경로가 `ai_interactions.model`에서 서로 구분되어야 사후에 비교 측정을 할 수 있다. */
  @Test
  void everyRouteIsRecordedUnderADistinctModelLabel() {
    when(gemini.answer(any(), any())).thenReturn(Optional.empty());
    coachService(true).answer(principal, request(OIL_FIRE));
    coachService(false).answer(principal, request(OIL_FIRE));
    coachService(false).answer(principal, request("국물이 좀 짜"));
    when(gemini.answer(any(), any())).thenReturn(Optional.of(REMOTE_ANSWER));
    coachService(false).answer(principal, request("면 얼마나 삶아"));

    ArgumentCaptor<String> models = ArgumentCaptor.forClass(String.class);
    verify(sessions, org.mockito.Mockito.times(4))
        .recordAiInteraction(any(), any(), models.capture(), any());
    assertThat(models.getAllValues())
        .containsExactly(
            "local-safety-intercept",
            "local-safety-fallback",
            "local-rule-fallback",
            "gemini-2.5-flash");
  }

  private CoachService coachService(boolean safetyInterceptEnabled) {
    return new CoachService(sessions, safetyRules, gemini, safetyInterceptEnabled);
  }

  private AiFeedbackRequest request(String speech) {
    return new AiFeedbackRequest(sessionId, speech, 0, 120);
  }

  private String recordedModel() {
    ArgumentCaptor<String> model = ArgumentCaptor.forClass(String.class);
    verify(sessions).recordAiInteraction(any(), any(), model.capture(), any());
    return model.getValue();
  }
}
