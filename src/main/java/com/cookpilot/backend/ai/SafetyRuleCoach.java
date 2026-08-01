package com.cookpilot.backend.ai;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Gemini보다 먼저 실행하는 보수적인 조리 안전 규칙.
 *
 * 안전 관련 질문을 외부 모델의 가용성이나 응답 품질에 맡기지 않는다. 규칙에 걸리면
 * Gemini를 호출하지 않으며, 어떤 행동도 자동 실행하도록 제안하지 않는다.
 */
@Component
class SafetyRuleCoach {

	private static final Pattern CLAUSE_BOUNDARY = Pattern.compile("[.!?。！？;；\\n\\r]+");
	private static final Pattern STEP_TARGET = Pattern.compile(
			"(?:다음|그\\s*다음|차기|후속)\\s*(?:번\\s*)?(?:조리\\s*)?단계"
					+ "|(?:[2-9]|[1-9][0-9]+)\\s*번?\\s*(?:조리\\s*)?단계"
					+ "|(?:다음|그\\s*다음)\\s*(?:순서|과정|단계)?");
	private static final Pattern POSITIVE_STEP_TRANSITION = Pattern.compile(
			"(?:넘어가(?:\\s*주세요|세요|십시오|도\\s*됩니다"
					+ "|면\\s*(?:됩니다|좋(?:습니다|아요))|겠습니다|자|는\\s*게\\s*좋)"
					+ "|넘어갑니다|이동(?:하세요|하십시오|합니다|해도\\s*됩니다|하면\\s*됩니다)"
					+ "|진행(?:하세요|하십시오|합니다|해도\\s*됩니다|하면\\s*됩니다)"
					+ "|전환(?:하세요|하십시오|합니다|해도\\s*됩니다|하면\\s*됩니다)"
					+ "|시작(?:하세요|하십시오|합니다|해도\\s*됩니다|하면\\s*됩니다)"
					+ "|(?<![가-힣])가(?:\\s*주세요|세요|십시오|도\\s*됩니다|면\\s*됩니다))");
	private static final Pattern POSITIVE_COMPLETION = Pattern.compile(
			"(?:조리|요리|레시피|음식)\\s*(?:를|을|가|이|는)?\\s*"
					+ "(?:완료(?:하세요|하십시오|합니다|해도\\s*됩니다|하면\\s*됩니다"
					+ "|됐(?:습니다|어요|으니|으므로|으니까)|되었(?:습니다|어요|으니|으므로|으니까)"
					+ "|입니다|예요|\\s*$)"
					+ "|완성(?:하세요|하십시오|합니다|됐(?:습니다|어요|으니|으므로|으니까)"
					+ "|되었(?:습니다|어요|으니|으므로|으니까)|입니다|예요|\\s*$)"
					+ "|끝(?:내세요|내십시오|냅니다|내(?:도|면)\\s*됩니다"
					+ "|났(?:습니다|어요|으니|으므로|으니까)"
					+ "|입니다|이에요|\\s*$)"
					+ "|종료(?:하세요|하십시오|합니다|됐(?:습니다|어요)|되었습니다|\\s*$)"
					+ "|마무리(?:하세요|하십시오|합니다|하시면\\s*됩니다)"
					+ "|마치(?:세요|십시오|면\\s*됩니다)|마쳤(?:습니다|어요|으니|으므로|으니까))"
					+ "|이제\\s*(?:모두\\s*)?다\\s*"
					+ "(?:됐(?:습니다|어요|으니|으므로|으니까)|되었습니다"
					+ "|끝났(?:습니다|어요|으니|으므로|으니까))");

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
	 * 모델은 조리 진행 상태를 소유하지 않는다. 따라서 구조화 응답이 유효하더라도
	 * 단계 이동이나 조리 완료를 긍정형으로 지시·선언한 문구는 신뢰하지 않는다.
	 * 부정형 경고(예: "넘어가지 마세요", "완료되지 않았어요")는 긍정형 어미와
	 * 일치하지 않으므로 허용한다.
	 */
	boolean directsStepTransitionOrCompletion(String... texts) {
		for (String text : texts) {
			if (text == null) {
				continue;
			}
			for (String clause : CLAUSE_BOUNDARY.split(normalize(text))) {
				if ((STEP_TARGET.matcher(clause).find()
						&& POSITIVE_STEP_TRANSITION.matcher(clause).find())
						|| POSITIVE_COMPLETION.matcher(clause).find()) {
					return true;
				}
			}
		}
		return false;
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
