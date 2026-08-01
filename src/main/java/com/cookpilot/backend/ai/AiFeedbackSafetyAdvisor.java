package com.cookpilot.backend.ai;

import java.util.Optional;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Gemini 전후의 F-08 안전 경계.
 *
 * 호출 전에는 서버 키워드 규칙으로 위험 질문을 차단하고, 호출 후에는 구조·문구·
 * 모델 안전 분류를 다시 검사한다. 안전 답변은 모델 문구 대신 서버 문구를 사용한다.
 */
@Component
class AiFeedbackSafetyAdvisor implements CallAdvisor {

	static final String CONTEXT_KEY =
			AiFeedbackSafetyAdvisor.class.getName() + ".context";

	private final SafetyRuleCoach safetyRuleCoach;
	private final ObjectMapper objectMapper;

	AiFeedbackSafetyAdvisor(
			SafetyRuleCoach safetyRuleCoach, ObjectMapper objectMapper) {
		this.safetyRuleCoach = safetyRuleCoach;
		this.objectMapper = objectMapper;
	}

	Optional<AiFeedbackAdvice> answerBeforeModel(AiFeedbackContext context) {
		return safetyRuleCoach.answer(context);
	}

	Optional<AiFeedbackAdvice> answerClassifiedProblem(String problem) {
		return safetyRuleCoach.answerClassifiedProblem(problem);
	}

	@Override
	public ChatClientResponse adviseCall(
			ChatClientRequest request, CallAdvisorChain chain) {
		Object contextValue = request.context().get(CONTEXT_KEY);
		if (contextValue instanceof AiFeedbackContext context) {
			answerBeforeModel(context).ifPresent(advice -> {
				throw new AiFeedbackSafetyDecisionException(advice);
			});
		}

		ChatClientResponse response = chain.nextCall(request);
		String content = responseText(response);
		AiFeedbackAdvice validated = AiFeedbackModelOutput
				.fromJsonStrict(objectMapper, content)
				.toValidatedAdvice(safetyRuleCoach);
		answerClassifiedProblem(validated.problem()).ifPresent(advice -> {
			throw new AiFeedbackSafetyDecisionException(advice);
		});
		return response;
	}

	private String responseText(ChatClientResponse response) {
		if (response == null
				|| response.chatResponse() == null
				|| response.chatResponse().getResult() == null
				|| response.chatResponse().getResult().getOutput() == null
				|| response.chatResponse().getResult().getOutput().getText() == null) {
			throw new IllegalArgumentException("F-08 모델 응답이 비어 있습니다.");
		}
		return response.chatResponse().getResult().getOutput().getText();
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return 100;
	}
}
