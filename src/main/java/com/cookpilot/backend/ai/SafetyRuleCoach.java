package com.cookpilot.backend.ai;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Gemini보다 먼저 실행하는 보수적인 조리 안전 규칙.
 *
 * 안전 관련 질문을 외부 모델의 가용성이나 응답 품질에 맡기지 않는다. 규칙에 걸리면
 * Gemini를 호출하지 않으며, 어떤 행동도 자동 실행하도록 제안하지 않는다.
 */
@Component
class SafetyRuleCoach {

	Optional<AiFeedbackAdvice> answer(AiFeedbackContext context) {
		String speech = normalize(context.userSpeech());
		String cookingContext = speech + " " + normalize(context.instruction());

		if (containsAny(speech,
				"불이 났", "불났", "화재", "기름불", "팬에 불", "냄비에 불", "연기가 나")) {
			return Optional.of(fireRisk());
		}

		if (containsAny(speech,
				"알레르기", "알러지", "두드러기", "입술이 부", "목이 부", "숨이 안 쉬", "호흡이 힘")) {
			return Optional.of(allergyRisk());
		}

		if (containsAny(speech,
				"상한 것", "상했", "썩은", "쉰내", "곰팡", "이상한 냄새", "부패")) {
			return Optional.of(spoilageRisk());
		}

		boolean undercooked = containsAny(speech,
				"안 익", "덜 익", "설익", "핏물", "속이 생", "속이 빨", "분홍색", "핑크색");
		boolean meat = containsAny(cookingContext,
				"닭", "돼지", "소고기", "쇠고기", "고기", "육류", "생선", "해산물", "계란");
		if (undercooked && meat) {
			return Optional.of(undercookedRisk());
		}

		if (containsAny(speech, "타는 냄새", "연기가 보여", "연기 나", "까맣게 타")) {
			return Optional.of(burningRisk());
		}

		return Optional.empty();
	}

	/**
	 * 키워드 규칙이 놓쳤더라도 Gemini가 안전 위험으로 분류한 경우 모델 문구를
	 * 그대로 신뢰하지 않고 서버가 소유한 보수적인 안내로 교체한다.
	 */
	Optional<AiFeedbackAdvice> answerClassifiedProblem(String problem) {
		if (problem == null) {
			return Optional.empty();
		}
		return switch (problem) {
			case "FIRE_RISK" -> Optional.of(fireRisk());
			case "ALLERGY_RISK" -> Optional.of(allergyRisk());
			case "SPOILAGE_RISK" -> Optional.of(spoilageRisk());
			case "UNDERCOOKED", "UNDERCOOKED_RISK" ->
					Optional.of(undercookedRisk());
			case "BURNING", "BURNING_RISK" -> Optional.of(burningRisk());
			default -> Optional.empty();
		};
	}

	private AiFeedbackAdvice fireRisk() {
		return new AiFeedbackAdvice(
				"조리를 멈추고 열원을 끄세요. 기름불에는 물을 붓지 말고, 위험하면 바로 대피해 긴급 도움을 요청하세요.",
				"즉시 조리를 멈추고 열원을 끄세요. 기름불에는 물을 붓지 말고, 안전하게 진압할 수 없으면 대피해 긴급 도움을 요청하세요.",
				null,
				"FIRE_RISK");
	}

	private AiFeedbackAdvice allergyRisk() {
		return new AiFeedbackAdvice(
				"섭취를 즉시 멈추세요. 붓기나 호흡 곤란이 있으면 지체하지 말고 긴급 의료 도움을 요청하세요.",
				"섭취를 중단하세요. 붓기·호흡 곤란 등 심한 증상이 있으면 즉시 긴급 의료 도움을 요청하세요.",
				null,
				"ALLERGY_RISK");
	}

	private AiFeedbackAdvice spoilageRisk() {
		return new AiFeedbackAdvice(
				"먹거나 맛보지 말고 버리세요. 의심되는 재료와 닿은 조리도구도 깨끗이 씻어주세요.",
				"맛보지 말고 폐기하세요. 의심 재료와 접촉한 손·조리도구·표면도 세척하세요.",
				null,
				"SPOILAGE_RISK");
	}

	private AiFeedbackAdvice undercookedRisk() {
		return new AiFeedbackAdvice(
				"먹지 말고 계속 익히세요. 가장 두꺼운 부분의 속까지 충분히 익었는지 확인한 뒤 드세요.",
				"섭취하지 말고 추가 가열하세요. 가장 두꺼운 부분의 중심까지 충분히 익었는지 확인하세요.",
				null,
				"UNDERCOOKED_RISK");
	}

	private AiFeedbackAdvice burningRisk() {
		return new AiFeedbackAdvice(
				"불을 끄거나 가장 약하게 낮추고, 냄비를 안전한 열원 밖으로 옮겨 상태를 확인하세요.",
				"열원을 끄거나 약하게 낮춘 뒤, 안전한 곳에서 음식과 조리도구 상태를 확인하세요.",
				null,
				"BURNING_RISK");
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
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
