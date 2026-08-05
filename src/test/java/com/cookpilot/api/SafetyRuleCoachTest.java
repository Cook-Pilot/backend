package com.cookpilot.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SafetyRuleCoachTest {
  private static final String SALTY_ADVICE = "물을 한 번에 많이 넣지 말고";
  private static final String BURNT_ADVICE = "불을 즉시 줄이고";

  private final SafetyRuleCoach coach = new SafetyRuleCoach();

  @Test
  void oilFireAdviceNeverSuggestsWater() {
    var response = coach.safetyAnswer("기름에 불이 붙었어").orElseThrow();

    assertThat(response.screenText()).contains("불을 끄고", "뚜껑", "물");
    assertThat(response.suggestedAction()).isNull();
  }

  @Test
  void undercookedMeatProvidesConservativeGuidanceWithoutMutation() {
    var response = coach.safetyAnswer("닭이 안 익은 것 같아").orElseThrow();

    assertThat(response.screenText()).contains("중심 온도", "시간만으로");
    assertThat(response.suggestedAction()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"간이 짜", "국물이 좀 짜", "소스가 너무 짠 것 같아", "진짜 너무 짜", "국이 너무 짜요", "양념이 짰어"})
  void saltyComplaintsGetSaltyAdvice(String speech) {
    assertThat(fallbackText(speech)).contains(SALTY_ADVICE);
  }

  /** `짜`가 다른 단어의 조각으로 들어간 발화. 이전에는 전부 짠맛 답변으로 잘못 흘렀다. */
  @ParameterizedTest
  @ValueSource(strings = {"진짜", "가짜", "계획을 짜", "이번 주 요리 계획을 짜", "이 음식 진짜", "이거 진짜 맞아"})
  void wordFragmentsOfSaltyTokenDoNotGetSaltyAdvice(String speech) {
    assertThat(fallbackText(speech)).doesNotContain(SALTY_ADVICE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"바닥이 눌어붙었어", "탄내가 나", "타는 냄새 나는데", "고기가 다 탔어", "겉이 까맣게 됐어", "소스가 타버렸어"})
  void burntComplaintsGetBurntAdvice(String speech) {
    assertThat(fallbackText(speech)).contains(BURNT_ADVICE);
  }

  /** `타` 한 글자가 조리 어휘의 조각으로 들어간 발화. 이전에는 전부 탄 것 답변으로 잘못 흘렀다. */
  @ParameterizedTest
  @ValueSource(strings = {"파스타 면 얼마나 삶아", "타이머 얼마나 남았어", "리코타 치즈 넣어도 돼", "토마토 언제 넣어"})
  void wordFragmentsOfBurntTokenDoNotGetBurntAdvice(String speech) {
    assertThat(fallbackText(speech)).doesNotContain(BURNT_ADVICE);
  }

  @Test
  void unmatchedSpeechStillGetsAnAnswer() {
    assertThat(fallbackText("음 잘 모르겠어")).isNotEmpty();
    assertThat(coach.fallbackAnswer("음 잘 모르겠어", context(0)).offlineFallback()).isTrue();
  }

  private String fallbackText(String speech) {
    return coach.fallbackAnswer(speech, context(0)).screenText();
  }

  private SessionContext context(Integer remainingSeconds) {
    return new SessionContext(
        UUID.randomUUID(), "토마토 파스타", UUID.randomUUID(), 0, "면을 삶는다", 300, remainingSeconds);
  }
}
