package com.cookpilot.backend.recommendation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.recommendation.explanation.RecommendationExplanationContext;
import com.cookpilot.backend.recommendation.explanation.RecommendationExplanationService;
import com.cookpilot.backend.user.UserService;
import lombok.RequiredArgsConstructor;

/**
 * 다음 조리 추천 조합. 조회는 {@link RecommendationDraftLoader}(트랜잭션),
 * 판정은 {@link RecommendationRuleEngine}(순수 함수), 설명 문구는
 * {@link RecommendationExplanationService}(외부 호출) 가 맡는다.
 *
 * 설명 생성은 트랜잭션 밖에서 부른다. Gemini 응답을 최대 수 초 기다리는 동안
 * DB 커넥션을 붙잡고 있으면 동시 요청 몇 건으로 풀이 마른다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

	private final RecommendationDraftLoader draftLoader;
	private final RecommendationExplanationService explanationService;
	private final UserService userService;

	public NextCookRecommendationResponse recommend(UUID recipeId) {
		UUID userId = userService.getCurrentUser().id();
		RecommendationDraftLoader.DraftBundle bundle =
				draftLoader.loadDrafts(userId, recipeId);
		List<RecommendationRuleEngine.Draft> drafts = bundle.drafts();
		if (drafts.isEmpty()) {
			return new NextCookRecommendationResponse(recipeId, Instant.now(), List.of());
		}

		List<RecommendationExplanationContext> contexts = drafts.stream()
				.map(draft -> new RecommendationExplanationContext(
						bundle.targetRecipeTitle(),
						draft.targetIngredient().name(),
						draft.targetIngredient().amount(),
						draft.suggestedAmount(),
						draft.targetIngredient().unit(),
						draft.changePercent(),
						draft.evidence()))
				.toList();
		List<RecommendationExplanationService.Explanation> explanations =
				explanationService.explainAll(contexts);

		List<NextCookRecommendation> recommendations = new ArrayList<>(drafts.size());
		for (int index = 0; index < drafts.size(); index++) {
			recommendations.add(toRecommendation(
					drafts.get(index), explanations.get(index)));
		}
		return new NextCookRecommendationResponse(recipeId, Instant.now(), recommendations);
	}

	private NextCookRecommendation toRecommendation(
			RecommendationRuleEngine.Draft draft,
			RecommendationExplanationService.Explanation explanation) {
		return new NextCookRecommendation(
				UUID.randomUUID(),
				"INGREDIENT_AMOUNT",
				draft.targetIngredient().id(),
				draft.targetIngredient().name(),
				draft.targetIngredient().amount(),
				draft.suggestedAmount(),
				draft.targetIngredient().unit(),
				draft.changePercent(),
				draft.confidence(),
				explanation.reason(),
				explanation.source(),
				explanation.model(),
				explanation.promptVersion(),
				draft.evidence());
	}
}
