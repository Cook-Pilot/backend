package com.cookpilot.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 무료 Gemini 프로젝트 쿼터를 한 사용자가 독점하지 않게 하는 F-08 호출 제한.
 */
@ConfigurationProperties("cookpilot.ai.feedback")
public record AiFeedbackRateLimitProperties(
		@DefaultValue("20") int requestsPerMinute
) {

	public AiFeedbackRateLimitProperties {
		if (requestsPerMinute < 1) {
			throw new IllegalArgumentException(
					"cookpilot.ai.feedback.requests-per-minute는 1 이상이어야 합니다.");
		}
	}
}
