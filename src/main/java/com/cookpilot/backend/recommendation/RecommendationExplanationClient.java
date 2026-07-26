package com.cookpilot.backend.recommendation;

import java.util.List;
import java.util.Optional;

public interface RecommendationExplanationClient {

	Optional<List<String>> explainAll(
			List<RecommendationExplanationContext> contexts);

	String model();
}
