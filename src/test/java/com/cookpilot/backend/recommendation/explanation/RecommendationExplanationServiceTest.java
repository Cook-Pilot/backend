package com.cookpilot.backend.recommendation.explanation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cookpilot.backend.recommendation.RecommendationEvidence;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationExplanationServiceTest {

	@Test
	void ai가_비활성화되면_규칙_근거로_추천_fallback을_만든다() {
		RecommendationExplanationClient disabledClient =
				new RecommendationExplanationClient() {
					@Override
					public Optional<List<String>> explainAll(
							List<RecommendationExplanationContext> contexts) {
						return Optional.empty();
					}

					@Override
					public String model() {
						return "test-model";
					}
				};
		RecommendationExplanationService service =
				new RecommendationExplanationService(disabledClient);
		RecommendationExplanationContext context =
				new RecommendationExplanationContext(
						"계란볶음밥",
						"진간장",
						new BigDecimal("0.5"),
						new BigDecimal("0.4"),
						"큰술",
						-20,
						List.of(
								new RecommendationEvidence(
										UUID.randomUUID(),
										UUID.randomUUID(),
										"두부조림",
										Instant.parse("2026-07-20T10:00:00Z"),
										5),
								new RecommendationEvidence(
										UUID.randomUUID(),
										UUID.randomUUID(),
										"제육볶음",
										Instant.parse("2026-07-21T10:00:00Z"),
										4)));

		RecommendationExplanationService.Explanation result =
				service.explainAll(List.of(context)).getFirst();

		assertThat(result.source()).isEqualTo("FALLBACK");
		assertThat(result.model()).isNull();
		assertThat(result.promptVersion())
				.isEqualTo(RecommendationExplanationService.PROMPT_VERSION);
		assertThat(result.reason())
				.contains("2회", "진간장", "20%", "0.4큰술");
	}
}
