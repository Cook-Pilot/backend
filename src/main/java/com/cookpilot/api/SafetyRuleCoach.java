package com.cookpilot.api;

import static com.cookpilot.api.ApiModels.AiFeedbackResponse;
import static com.cookpilot.api.ApiModels.SuggestedAction;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class SafetyRuleCoach {
  Optional<AiFeedbackResponse> safetyAnswer(String speech) {
    String text = normalize(speech);
    if (containsAny(text, "기름에불", "불이붙", "불났", "연기가너무")) {
      return Optional.of(answer("불을 끄고 냄비나 팬을 뚜껑으로 덮으세요. 기름불에는 절대 물을 붓지 마세요."));
    }
    if (containsAny(text, "상한", "곰팡", "이상한냄새", "썩은", "변질")) {
      return Optional.of(answer("변질이 의심되면 맛보지 말고 버리세요. 새 재료로 다시 조리하는 것이 안전합니다."));
    }
    if (containsAny(text, "알레르기", "알러지")) {
      return Optional.of(answer("알레르기 유발 가능성이 있으면 해당 재료와 같은 조리도구 사용을 중단하고 성분표를 확인하세요."));
    }
    if (containsAny(text, "생고기", "피가나", "속이빨", "닭이안익", "돼지고기안익")) {
      return Optional.of(
          new AiFeedbackResponse(
              "먹지 말고 중심부가 안전하게 익었는지 식품용 온도계로 확인하세요. 색이나 시간만으로 판단하지 마세요.",
              "먹지 말고 중심 온도를 확인하세요. 색이나 시간만으로 익힘을 판단하지 마세요.",
              null,
              true));
    }
    return Optional.empty();
  }

  AiFeedbackResponse fallbackAnswer(String speech, SessionContext context) {
    String text = normalize(speech);
    if (containsAny(text, "안끓", "물이끓지", "기포가안")) {
      return extend("불을 한 단계 올리고 뚜껑을 덮어 1분 더 기다린 뒤 기포를 확인하세요.", 60);
    }
    if (containsAny(text, "덜익", "안익", "설익")) {
      return extend("현재 불 세기를 유지하고 1분 더 익힌 뒤 가장 두꺼운 부분을 확인하세요.", 60);
    }
    if (isSaltyDescription(text)) {
      return answer("물을 한 번에 많이 넣지 말고 한 스푼씩 추가해 간을 다시 확인하세요.");
    }
    if (containsAny(text, "싱거", "간이약")) {
      return answer("양념을 한 꼬집만 추가하고 20초 더 섞은 뒤 다시 맛보세요.");
    }
    if (isBurntDescription(text)) {
      return answer("불을 즉시 줄이고 바닥을 긁지 않은 채 윗부분만 새 팬으로 옮기세요.");
    }
    if (containsAny(text, "없어", "대신", "대체")) {
      return answer("해당 재료는 우선 빼고 조리하되, 양념은 절반만 넣은 뒤 마지막에 맛을 조절하세요.");
    }
    if (context.remainingSeconds() != null && context.remainingSeconds() > 0) {
      return extend("현재 단계를 30초 더 진행하고 상태가 달라지는지 확인하세요.", 30);
    }
    return answer("불을 약하게 유지하고 현재 상태를 30초 뒤 다시 확인하세요.");
  }

  private AiFeedbackResponse extend(String text, int seconds) {
    return new AiFeedbackResponse(text, text, new SuggestedAction("EXTEND_TIMER", seconds), true);
  }

  private AiFeedbackResponse answer(String text) {
    return new AiFeedbackResponse(text, text, null, true);
  }

  /**
   * 짠맛 호소인지 판별한다. 맛/조리 맥락어가 함께 있을 때만 인정하고, `진짜`·`가짜`처럼 `짜`가 다른 단어의 조각으로
   * 들어간 경우는 제외한다. 프론트 {@code LocalCoach._isSaltyDescription}와 같은 규칙이다.
   */
  private boolean isSaltyDescription(String text) {
    if (containsAny(text, "계획을짜", "일정을짜", "각본을짜", "전략을짜")) {
      return false;
    }
    if (!containsAny(
        text, "간이", "국이", "국물", "소스", "양념", "찌개", "육수", "반찬", "음식", "요리", "너무", "좀", "조금", "약간")) {
      return false;
    }
    if (containsAny(text, "짠", "짰")) {
      return true;
    }
    for (int index = text.indexOf('짜'); index >= 0; index = text.indexOf('짜', index + 1)) {
      char previous = index == 0 ? ' ' : text.charAt(index - 1);
      if (previous != '진' && previous != '가') {
        return true;
      }
    }
    return false;
  }

  /**
   * 탄 것에 대한 호소인지 판별한다. `타` 한 글자는 `파스타`, `타이머`, `리코타`처럼 흔한 조리 어휘의 조각과
   * 충돌하므로 쓰지 않고, 조각으로 나타날 수 없는 2음절 이상 형태만 인정한다.
   */
  private boolean isBurntDescription(String text) {
    return containsAny(text, "눌어붙", "탄내", "타는냄새", "타버", "타고있", "탔", "까맣게");
  }

  private String normalize(String speech) {
    return speech == null ? "" : speech.replace(" ", "");
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
