package com.cookpilot.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SafetyRuleCoachTest {
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
}
