package com.cookpilot.backend.ai;

import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.stereotype.Component;

/**
 * 모델·전송·구조화 변환 실패를 한 경계에서 흡수한다.
 *
 * 사용자 발화, 프롬프트, 응답, API 키는 로그에 넣지 않고 실패 타입만 기록한다.
 */
@Component
class AiFeedbackFallbackAdvisor implements CallAdvisor {

	private static final Logger log =
			LoggerFactory.getLogger(AiFeedbackFallbackAdvisor.class);

	@Override
	public ChatClientResponse adviseCall(
			ChatClientRequest request, CallAdvisorChain chain) {
		try {
			return chain.nextCall(request);
		} catch (AiFeedbackSafetyDecisionException exception) {
			throw exception;
		} catch (AiFeedbackGenerationException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new AiFeedbackGenerationException(exception);
		}
	}

	<T> Optional<T> execute(Supplier<T> operation) {
		try {
			return Optional.ofNullable(operation.get());
		} catch (AiFeedbackSafetyDecisionException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			Throwable diagnostic = exception instanceof AiFeedbackGenerationException
					&& exception.getCause() != null
					? exception.getCause()
					: exception;
			log.warn("Gemini 조리 도움 생성에 실패해 F-08 fallback을 사용합니다 ({})",
					diagnostic.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return 0;
	}
}
