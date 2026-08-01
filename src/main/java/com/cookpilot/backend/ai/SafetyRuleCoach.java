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
	private static final String HAZARD_SUBJECT_PARTICLE =
			"(?:이|가|은|는|도|만|조차|마저|까지(?:는|도|만)?)";
	private static final String HAZARD_MODIFIER =
			"(?:갑자기|방금|지금|아까부터|계속|자꾸|점점|조금|약간|매우"
					+ "|좀|너무|아주|정말|엄청|많이|확|막"
					+ "|[가-힣]{1,16}(?:게|히|보다(?:도)?))";
	private static final String HAZARD_MODIFIERS =
			"(?:(?:" + HAZARD_MODIFIER + ")\\s+){0,5}";
	private static final String HAZARD_REPORT_PREFIX =
			"(?:\\s*" + HAZARD_SUBJECT_PARTICLE + "\\s*" + HAZARD_MODIFIERS
					+ "|\\s+" + HAZARD_MODIFIERS
					+ "|\\s*)";
	private static final String ACTIVE_HAZARD_REPORT =
			"(?:나(?=\\s*$)|나요|납니다|나네(?:요)?|나는데(?:요)?|나서(?:요)?"
					+ "|나고\\s*있(?:어(?:요)?|습니다)"
					+ "|나기\\s*시작(?:해(?:요)?|했(?:어(?:요)?|습니다))"
					+ "|나는\\s*중(?:입니다|이에요|예요)|난다"
					+ "|난\\s*것\\s*같(?:아(?:요)?|습니다)"
					+ "|났(?=\\s*$)|났어(?:요)?|났습니다|났네(?:요)?|났는데(?:요)?|났다)";
	private static final String ACTIVE_FIRE_SPREAD_REPORT =
			"(?:붙어(?:요)?|붙었(?:어(?:요)?|습니다|네(?:요)?)"
					+ "|붙은\\s*것\\s*같(?:아(?:요)?|습니다)"
					+ "|번져(?:요)?|번집니다|번졌(?:어(?:요)?|습니다|네(?:요)?)"
					+ "|번지고\\s*있(?:어(?:요)?|습니다)"
					+ "|번지는\\s*중(?:입니다|이에요|예요))";
	private static final String ACTIVE_FIRE_EVENT_REPORT =
			"(?:" + ACTIVE_HAZARD_REPORT
					+ "|" + ACTIVE_FIRE_SPREAD_REPORT
					+ "|발생했(?:어(?:요)?|습니다|네(?:요)?)"
					+ "|발생하고\\s*있(?:어(?:요)?|습니다))";
	private static final String HAZARD_REPORT_BOUNDARY =
			"(?=$|\\s|[.!?。！？,，;；\\n\\r\"'”’」』])";
	private static final Pattern ACTIVE_SMOKE_OR_FIRE_RISK = Pattern.compile(
			"(?<![\\p{L}\\p{N}])(?:연기|불)"
					+ HAZARD_REPORT_PREFIX
					+ "(?:" + ACTIVE_HAZARD_REPORT
					+ "|" + ACTIVE_FIRE_SPREAD_REPORT
					+ "|(?:입니다|이에요|예요))"
					+ HAZARD_REPORT_BOUNDARY);
	private static final Pattern ACTIVE_EXPLICIT_FIRE_RISK = Pattern.compile(
			"(?<![\\p{L}\\p{N}])(?:화재|기름\\s*불|산불)"
					+ "(?:" + HAZARD_REPORT_PREFIX
					+ "(?:" + ACTIVE_FIRE_EVENT_REPORT + "|(?:입니다|이에요|예요))"
					+ HAZARD_REPORT_BOUNDARY
					+ "|\\s*(?=$))");
	private static final Pattern HAZARD_RETRACTION_PREFIX = Pattern.compile(
			"^\\s*(?:[\\\"'”’」』]?\\s*(?:라는|라고|이라고)\\s*"
					+ "(?:(?:말|내용|표현)(?:은|는|이|가)?\\s*)?"
					+ "(?:사실이\\s*)?"
					+ "(?:아닙니다|아니에요|아니야|아니고|아니지만|틀렸(?:습니다|어요))"
					+ "|[,，]\\s*(?:아니|정정하면|사실은)\\s*"
					+ "(?:(?:연기|불)(?:이|가)?\\s*아니라\\s*)?"
					+ "(?:수증기|김|안개)(?:입니다|이에요|예요)"
					+ "|[,，]\\s*(?:아니|정정하면|사실은)\\s*"
					+ "(?:지금은\\s*)?(?:(?:불|연기)(?:은|는|이|가)?\\s*)?"
					+ "(?:꺼졌(?:어(?:요)?|습니다)"
					+ "|꺼져\\s*있(?:어(?:요)?|습니다)"
					+ "|멈췄(?:어(?:요)?|습니다)))");
	private static final String STEP_REFERENCE =
			"(?:(?:다음|그\\s*다음|차기|후속)\\s*(?:번\\s*)?(?:조리\\s*)?단계"
					+ "|(?:다음|그\\s*다음|차기|후속)\\s*(?:순서|과정)"
					+ "|(?:다음|그\\s*다음)(?=\\s*(?:로|으로))"
					+ "|[1-9][0-9]*\\s*(?:(?:번|번째)\\s*)?(?:조리\\s*)?단계)";
	private static final String TRANSITION_PERMISSION =
			"(?:됩니다|돼요|좋(?:습니다|아요)|괜찮(?:습니다|아요))";
	private static final String TRANSITION_PARTICLE =
			"(?:에선|(?:로|으로|를|을|에|에서|까지|부터)(?:는|만|도)?"
					+ "|(?:은|는|만|도))";
	private static final String TRANSITION_ADVERB =
			"(?:꼭|반드시|무조건|즉시|바로|곧|곧장|먼저|이제|한\\s*번"
					+ "|천천히|서서히|[가-힣]{1,12}게)";
	private static final String TRANSITION_ADVERBS =
			"(?:(?:" + TRANSITION_ADVERB + ")\\s*){0,2}";
	private static final String TRANSITION_COOKING_OBJECT =
			"(?:(?:조리|요리)\\s*(?:(?:를|을)\\s*)?)?";
	private static final String COOKING_SUBJECT_PARTICLE =
			"(?:(?:를|을|가|이|까지|부터)(?:는|도|만)?"
					+ "|(?:은|는|도|만|조차|마저))";
	private static final String COOKING_SUBJECT =
			"(?:조리|요리|레시피|음식)\\s*"
					+ "(?:(?:" + COOKING_SUBJECT_PARTICLE + ")\\s*)?";
	private static final String COMPLETION_ENDING =
			"(?:습니다|어요|네요|군요|지만|으니|으므로|으니까|고(?:요)?)";
	private static final String COMPLETED_STATE =
			"(?:(?:됐|되었|끝났)" + COMPLETION_ENDING
					+ "|되어\\s*(?:있(?:습니다|어요|네요|군요|지만)"
					+ "|있으(?:니|므로|니까))"
					+ "|된\\s*상태(?:입니다|이에요|예요|네요|지만))";
	private static final String ALL_DONE_PREFIX =
			"(?:(?:이제|벌써|이미)\\s*)?(?:(?:모두|전부)\\s*)?다\\s*";
	private static final String POLITE_REQUEST_ENDING =
			"(?:주세요|주십시오|줘(?:요)?|주시기\\s*바랍니다|주시면\\s*됩니다"
					+ "|주셨으면\\s*합니다|주시겠(?:어요|습니까))";
	private static final String VERB_REQUEST_SUFFIX =
			"\\s*" + POLITE_REQUEST_ENDING;
	private static final String VERB_PERMISSION_SUFFIX =
			"(?:도|셔도|면|시면)\\s*" + TRANSITION_PERMISSION;
	private static final String NOMINAL_ACTION_REQUEST =
			"(?:해|하여)" + VERB_REQUEST_SUFFIX;
	private static final String NOMINAL_ACTION_PERMISSION =
			"(?:해도|하셔도|하면|하시면)\\s*" + TRANSITION_PERMISSION;
	private static final String ACTION_RECOMMENDATION_SUFFIX =
			"\\s*(?:을|를)?\\s*(?:권해\\s*드립니다|권장(?:합니다|드립니다)"
					+ "|추천(?:합니다|드립니다)|제안(?:합니다|드립니다))";
	private static final String NOMINAL_ACTION_DIRECTIVE =
			"(?:하세요|하십시오|합니다|" + NOMINAL_ACTION_REQUEST
					+ "|" + NOMINAL_ACTION_PERMISSION
					+ "|\\s*부탁드(?:립니다|릴게요|려요)"
					+ "|" + ACTION_RECOMMENDATION_SUFFIX + ")";
	private static final String TRANSITION_ACTION_DIRECTIVE =
			"(?:(?:넘어가|가)(?:세요|십시오|" + VERB_REQUEST_SUFFIX
					+ "|" + VERB_PERMISSION_SUFFIX + ")"
					+ "|넘어가(?:겠습니다|자|는\\s*게\\s*(?:좋(?:습니다|아요)|낫(?:습니다|아요)))"
					+ "|넘어갑니다|넘어갈게요|넘어갑시다"
					+ "|(?:이동|진행|전환|시작)" + NOMINAL_ACTION_DIRECTIVE + ")";
	private static final String COMPLETION_ACTION_DIRECTIVE =
			"(?:(?:완료|완성|종료|마무리)\\s*" + NOMINAL_ACTION_DIRECTIVE
					+ "|끝(?:내(?:세요|십시오|" + VERB_REQUEST_SUFFIX
					+ "|" + VERB_PERMISSION_SUFFIX + ")|냅니다)"
					+ "|마치(?:세요|십시오|" + VERB_PERMISSION_SUFFIX + ")"
					+ "|마칩니다"
					+ "|마쳐(?:" + VERB_REQUEST_SUFFIX
					+ "|도\\s*" + TRANSITION_PERMISSION + "))";
	private static final Pattern POSITIVE_STEP_TRANSITION = Pattern.compile(
			STEP_REFERENCE
					+ "\\s*(?:" + TRANSITION_PARTICLE + "\\s*)?"
					+ TRANSITION_ADVERBS
					+ TRANSITION_COOKING_OBJECT
					+ TRANSITION_ADVERBS
					+ TRANSITION_ACTION_DIRECTIVE);
	private static final String COMPLETION_DECLARATION =
			"(?:완료\\s*(?:됐(?:습니다|어요|으니|으므로|으니까)"
					+ "|되었(?:습니다|어요|으니|으므로|으니까)|입니다|예요|(?=\\s*$))"
					+ "|완성\\s*(?:됐(?:습니다|어요|으니|으므로|으니까)"
					+ "|되었(?:습니다|어요|으니|으므로|으니까)|입니다|예요|(?=\\s*$))"
					+ "|끝(?:났(?:습니다|어요|으니|으므로|으니까)|입니다|이에요|(?=\\s*$))"
					+ "|종료\\s*(?:됐(?:습니다|어요)|되었습니다|(?=\\s*$))"
					+ "|마쳤(?:습니다|어요|으니|으므로|으니까)"
					+ "|" + ALL_DONE_PREFIX + COMPLETED_STATE + ")";
	private static final Pattern POSITIVE_COMPLETION = Pattern.compile(
			"(?:" + COOKING_SUBJECT
					+ "(?:" + COMPLETION_ACTION_DIRECTIVE
					+ "|" + COMPLETION_DECLARATION + ")"
					+ "|^\\s*" + ALL_DONE_PREFIX + COMPLETED_STATE + ")");
	private static final Pattern DIRECTIVE_RETRACTION_PREFIX = Pattern.compile(
			"^\\s*(?:[\\\"'”’」』]?\\s*(?:라는|라고|이라고)\\s*"
					+ "(?:(?:문구|안내|말)(?:은|는|을|를)?\\s*)?"
					+ "(?:따르지|따라가지|무시|사용하지|말하지|하지)"
					+ "(?:\\s*(?:마세요|말고))?\\s*"
					+ "|(?:이|가)?\\s*아니라\\s*)");
	private static final String REPLACEMENT_LEAD =
			"(?:(?:지금|이제|바로|대신|그보다)\\s*)?";
	private static final Pattern STEP_REPLACEMENT_DIRECTIVE = Pattern.compile(
			"^\\s*" + REPLACEMENT_LEAD + TRANSITION_ACTION_DIRECTIVE);
	private static final Pattern COMPLETION_REPLACEMENT_DIRECTIVE = Pattern.compile(
			"^\\s*" + REPLACEMENT_LEAD + COMPLETION_ACTION_DIRECTIVE);

	Optional<AiFeedbackAdvice> answer(AiFeedbackContext context) {
		String speech = normalize(context.userSpeech());
		String cookingContext = speech + " " + normalize(context.instruction());

		if (reportsActiveFireRisk(speech)) {
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

		if (containsAny(speech, "타는 냄새", "연기가 보여", "까맣게 타")) {
			return Optional.of(burningRisk());
		}

		return Optional.empty();
	}

	private boolean reportsActiveFireRisk(String speech) {
		for (String clause : CLAUSE_BOUNDARY.split(speech)) {
			if (containsUnretractedHazard(ACTIVE_SMOKE_OR_FIRE_RISK, clause)
					|| containsUnretractedHazard(ACTIVE_EXPLICIT_FIRE_RISK, clause)) {
				return true;
			}
		}
		return false;
	}

	private boolean containsUnretractedHazard(Pattern hazard, String clause) {
		var matcher = hazard.matcher(clause);
		while (matcher.find()) {
			String suffix = clause.substring(matcher.end());
			if (!HAZARD_RETRACTION_PREFIX.matcher(suffix).find()) {
				return true;
			}
		}
		return false;
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
				if (containsUnretractedDirective(
						POSITIVE_STEP_TRANSITION,
						STEP_REPLACEMENT_DIRECTIVE,
						clause)
						|| containsUnretractedDirective(
								POSITIVE_COMPLETION,
								COMPLETION_REPLACEMENT_DIRECTIVE,
								clause)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean containsUnretractedDirective(
			Pattern directive, Pattern replacement, String clause) {
		var matcher = directive.matcher(clause);
		while (matcher.find()) {
			String suffix = clause.substring(matcher.end());
			var retraction = DIRECTIVE_RETRACTION_PREFIX.matcher(suffix);
			if (!retraction.find()) {
				return true;
			}
			String remainder = suffix.substring(retraction.end());
			if (replacement.matcher(remainder).find()) {
				return true;
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
