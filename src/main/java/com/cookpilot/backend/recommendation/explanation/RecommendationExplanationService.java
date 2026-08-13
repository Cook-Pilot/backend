package com.cookpilot.backend.recommendation.explanation;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecommendationExplanationService {

	/**
	 * 생성된 설명마다 recommendation_feedback.prompt_version 에 저장돼, 나중에 "이 문구가
	 * 어느 프롬프트에서 나왔는지" 역추적하는 값이다. 기능 번호(F-xx)는 명세 개정마다 밀리므로
	 * 저장값을 거기 묶지 않는다. 프롬프트 문구를 바꾸면 v2, v3 로 올린다.
	 */
	public static final String PROMPT_VERSION = "nextcook-reason-v1";

	private final RecommendationExplanationClient explanationClient;

	public List<Explanation> explainAll(
			List<RecommendationExplanationContext> contexts) {
		if (contexts.isEmpty()) {
			return List.of();
		}
		List<String> generated = explanationClient.explainAll(contexts)
				.filter(reasons -> reasons.size() == contexts.size())
				.orElse(null);
		if (generated == null) {
			return contexts.stream()
					.map(context -> new Explanation(
							fallback(context), "FALLBACK", null, PROMPT_VERSION))
					.toList();
		}
		String model = explanationClient.model();
		return generated.stream()
				.map(reason -> new Explanation(reason, "GEMINI", model, PROMPT_VERSION))
				.toList();
	}

	private String fallback(RecommendationExplanationContext context) {
		int evidenceCount = context.evidence().size();
		int absolutePercent = Math.abs(context.changePercent());
		String direction = context.changePercent() < 0 ? "줄인" : "늘린";
		BigDecimal amount = context.suggestedAmount().stripTrailingZeros();
		return "최근 비슷한 요리 %d회의 만족도 높은 기록을 바탕으로 재료 %s의 양을 "
				.concat("%d%% %s %s%s로 시작해 볼까요?")
				.formatted(
						evidenceCount,
						context.ingredientName(),
						absolutePercent,
						direction,
						amount.toPlainString(),
						context.unit());
	}

	public record Explanation(
			String reason,
			String source,
			String model,
			String promptVersion
	) {
	}
}
