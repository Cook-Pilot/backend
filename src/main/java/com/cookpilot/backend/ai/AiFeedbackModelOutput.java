package com.cookpilot.backend.ai;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring AI native structured output으로 Gemini에 강제하는 F-08 전용 모델 응답.
 *
 * provider schema 뒤에서도 서버가 정확한 필드 집합, 길이, 행동 값을 다시 검증한다.
 */
public record AiFeedbackModelOutput(
		String speechText,
		String screenText,
		Problem problem,
		@Nullable SuggestedActionOutput suggestedAction
) {

	private static final int MAX_SPEECH_LENGTH = 240;
	private static final int MAX_SCREEN_LENGTH = 400;

	static AiFeedbackModelOutput fromJsonStrict(
			ObjectMapper objectMapper, String content) {
		JsonNode root = objectMapper.reader()
				.with(
						DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
						DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(content);
		if (!hasExactStructure(root)) {
			throw new IllegalArgumentException("F-08 모델 응답 구조가 올바르지 않습니다.");
		}
		return objectMapper.treeToValue(root, AiFeedbackModelOutput.class);
	}

	AiFeedbackAdvice toValidatedAdvice(SafetyRuleCoach safetyRuleCoach) {
		String validatedSpeech = validatedText(speechText, MAX_SPEECH_LENGTH);
		String validatedScreen = validatedText(screenText, MAX_SCREEN_LENGTH);
		if (problem == null
				|| safetyRuleCoach.directsStepTransitionOrCompletion(
						validatedSpeech, validatedScreen)) {
			throw new IllegalArgumentException("F-08 모델 응답 의미가 허용 범위를 벗어났습니다.");
		}

		AiFeedbackResponse.SuggestedAction action = null;
		if (suggestedAction != null) {
			if (suggestedAction.type() != ActionType.EXTEND_TIMER
					|| suggestedAction.seconds() == null
					|| (suggestedAction.seconds() != 30
						&& suggestedAction.seconds() != 60)) {
				throw new IllegalArgumentException("F-08 모델 행동 제안이 허용 범위를 벗어났습니다.");
			}
			action = new AiFeedbackResponse.SuggestedAction(
					suggestedAction.type().name(), suggestedAction.seconds());
		}

		return new AiFeedbackAdvice(
				validatedSpeech,
				validatedScreen,
				action,
				problem.name());
	}

	private static boolean hasExactStructure(JsonNode root) {
		if (root == null
				|| !root.isObject()
				|| root.size() < 3
				|| root.size() > 4
				|| !isText(root.get("speechText"))
				|| !isText(root.get("screenText"))
				|| !isText(root.get("problem"))) {
			return false;
		}

		JsonNode action = root.get("suggestedAction");
		// Spring AI native schema는 nullable 객체를 선택 필드로 표현한다.
		if (action == null) {
			return root.size() == 3;
		}
		if (action.isNull()) {
			return true;
		}
		return action.isObject()
				&& action.size() == 2
				&& isText(action.get("type"))
				&& action.get("seconds") != null
				&& action.get("seconds").isIntegralNumber();
	}

	private static boolean isText(JsonNode value) {
		return value != null && value.isString();
	}

	private static String validatedText(String value, int maxLength) {
		if (value == null) {
			throw new IllegalArgumentException("F-08 모델 답변 문구가 없습니다.");
		}
		String trimmed = value.trim();
		if (trimmed.isBlank()
				|| trimmed.length() > maxLength
				|| trimmed.contains("\n")
				|| trimmed.contains("\r")) {
			throw new IllegalArgumentException("F-08 모델 답변 문구가 허용 범위를 벗어났습니다.");
		}
		return trimmed;
	}

	public enum Problem {
		WATER_NOT_BOILING,
		TOO_SALTY,
		TOO_BLAND,
		BURNING,
		UNDERCOOKED,
		MISSING_INGREDIENT,
		FIRE_RISK,
		ALLERGY_RISK,
		SPOILAGE_RISK,
		OTHER
	}

	public enum ActionType {
		EXTEND_TIMER
	}

	public record SuggestedActionOutput(ActionType type, Integer seconds) {
	}
}
