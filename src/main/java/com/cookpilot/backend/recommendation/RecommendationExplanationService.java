package com.cookpilot.backend.recommendation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class RecommendationExplanationService {

	public static final String PROMPT_VERSION = "f11-reason-v1";

	private final RecommendationExplanationClient explanationClient;

	public RecommendationExplanationService(
			RecommendationExplanationClient explanationClient) {
		this.explanationClient = explanationClient;
	}

	public List<Explanation> explainAll(
			List<RecommendationExplanationContext> contexts) {
		if (contexts.isEmpty()) {
			return List.of();
		}
		List<String> generated = explanationClient.explainAll(contexts)
				.filter(reasons -> reasons.size() == contexts.size())
				.orElse(null);
		List<Explanation> results = new ArrayList<>(contexts.size());
		for (int index = 0; index < contexts.size(); index++) {
			RecommendationExplanationContext context = contexts.get(index);
			if (generated != null) {
				results.add(new Explanation(
						generated.get(index),
						"GEMINI",
						explanationClient.model(),
						PROMPT_VERSION));
			} else {
				results.add(new Explanation(
						fallback(context),
						"FALLBACK",
						null,
						PROMPT_VERSION));
			}
		}
		return List.copyOf(results);
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
