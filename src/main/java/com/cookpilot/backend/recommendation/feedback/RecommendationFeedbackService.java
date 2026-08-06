package com.cookpilot.backend.recommendation.feedback;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.recipe.RecipeIngredientEntity;
import com.cookpilot.backend.recipe.RecipeIngredientRepository;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.review.PostCookReviewRepository;
import com.cookpilot.backend.user.UserService;

/**
 * 추천 피드백 기록과 조회. 추천 생성(읽기 전용)과 트랜잭션 성격이 달라 분리했다.
 *
 * 추천 자체는 서버에 저장되지 않으므로(추천 id 는 매 요청 새로 생성) 여기 쌓이는
 * 수락·거절·수정 기록만이 추천 정책을 평가할 유일한 자료다.
 */
@Service
public class RecommendationFeedbackService {

	private static final int MAX_REASON_LENGTH = 500;
	private static final int MAX_EVIDENCE_REVIEW_IDS = 10;
	private static final Set<String> EXPLANATION_SOURCES = Set.of("GEMINI", "FALLBACK");

	private final RecommendationFeedbackRepository feedbackRepository;
	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final PostCookReviewRepository reviewRepository;
	private final UserService userService;

	public RecommendationFeedbackService(
			RecommendationFeedbackRepository feedbackRepository,
			RecipeRepository recipeRepository,
			RecipeIngredientRepository recipeIngredientRepository,
			PostCookReviewRepository reviewRepository,
			UserService userService) {
		this.feedbackRepository = feedbackRepository;
		this.recipeRepository = recipeRepository;
		this.recipeIngredientRepository = recipeIngredientRepository;
		this.reviewRepository = reviewRepository;
		this.userService = userService;
	}

	/**
	 * 재료별 "가장 최근 피드백이 거절인 경우"의 거절 시각.
	 *
	 * 최신 피드백이 수락이면 그 재료는 결과에 없다(과거에 거절한 적이 있어도 막지 않는다).
	 * 이 시각 이후로 새 조리 기록이 생겼는지는 규칙 엔진이 판단한다.
	 */
	@Transactional(readOnly = true)
	public Map<UUID, Instant> latestRejectionByIngredient(UUID userId, UUID recipeId) {
		Map<UUID, Instant> rejections = new HashMap<>();
		Set<UUID> seen = new HashSet<>();
		for (RecommendationFeedbackEntity item : feedbackRepository
				.findByUserIdAndRecipeIdOrderByCreatedAtDesc(userId, recipeId)) {
			UUID ingredientId = item.getOriginalIngredientId();
			if (ingredientId == null || !seen.add(ingredientId)) {
				continue; // 정렬이 최신순이라 재료별 첫 행만 최신 피드백이다.
			}
			if (item.getDecision() == RecommendationDecision.REJECTED) {
				rejections.put(ingredientId, item.getCreatedAt());
			}
		}
		return rejections;
	}

	/** 같은 추천에 두 번 보내면 처음 저장된 것을 그대로 돌려준다(멱등). */
	@Transactional
	public RecommendationFeedbackResponse recordFeedback(
			UUID recipeId,
			UUID recommendationId,
			SubmitRecommendationFeedbackRequest request) {
		UUID userId = userService.lockCurrentUser().id();
		validateFeedbackRequest(userId, recipeId, request);
		RecommendationFeedbackEntity existing = feedbackRepository
				.findByUserIdAndRecommendationId(userId, recommendationId)
				.orElse(null);
		if (existing != null) {
			return RecommendationFeedbackResponse.from(existing);
		}

		BigDecimal appliedAmount = switch (request.decision()) {
			case REJECTED -> null;
			case ACCEPTED -> request.appliedAmount() != null
					? request.appliedAmount()
					: request.suggestedAmount();
			case MODIFIED -> request.appliedAmount();
		};
		RecommendationFeedbackEntity saved = feedbackRepository.saveAndFlush(
				RecommendationFeedbackEntity.builder()
						.recommendationId(recommendationId)
						.userId(userId)
						.recipeId(recipeId)
						.originalIngredientId(request.originalIngredientId())
						.decision(request.decision())
						.originalAmount(request.originalAmount())
						.suggestedAmount(request.suggestedAmount())
						.appliedAmount(appliedAmount)
						.unit(trimToNull(request.unit()))
						.reason(limitReason(request.reason()))
						.explanationSource(request.explanationSource())
						.model(trimToNull(request.model()))
						.promptVersion(request.promptVersion().trim())
						.evidenceReviewIds(request.evidenceReviewIds())
						.build());
		return RecommendationFeedbackResponse.from(saved);
	}

	private void validateFeedbackRequest(
			UUID userId,
			UUID recipeId,
			SubmitRecommendationFeedbackRequest request) {
		if (!recipeRepository.existsById(recipeId)) {
			throw new NotFoundException("레시피를 찾을 수 없습니다: " + recipeId);
		}
		RecipeIngredientEntity ingredient = recipeIngredientRepository
				.findById(request.originalIngredientId())
				.orElseThrow(() -> new NotFoundException(
						"재료를 찾을 수 없습니다: " + request.originalIngredientId()));
		if (!ingredient.getRecipeId().equals(recipeId)) {
			throw new IllegalArgumentException("추천 재료가 현재 레시피에 속하지 않습니다.");
		}
		if (request.originalAmount().signum() < 0
				|| request.suggestedAmount().signum() < 0
				|| request.appliedAmount() != null
						&& request.appliedAmount().signum() < 0) {
			throw new IllegalArgumentException("추천 재료 양은 0 이상이어야 합니다.");
		}
		if (request.decision() == RecommendationDecision.MODIFIED
				&& request.appliedAmount() == null) {
			throw new IllegalArgumentException("수정 수락에는 appliedAmount가 필요합니다.");
		}
		if (!EXPLANATION_SOURCES.contains(request.explanationSource())) {
			throw new IllegalArgumentException("추천 설명 출처가 올바르지 않습니다.");
		}
		if (request.evidenceReviewIds().size() > MAX_EVIDENCE_REVIEW_IDS) {
			throw new IllegalArgumentException(
					"추천 근거는 최대 %d개까지 저장할 수 있습니다.".formatted(MAX_EVIDENCE_REVIEW_IDS));
		}
		Set<UUID> evidenceReviewIds = new HashSet<>(request.evidenceReviewIds());
		if (!evidenceReviewIds.isEmpty()
				&& reviewRepository.findByUserIdAndIdIn(userId, evidenceReviewIds).size()
						!= evidenceReviewIds.size()) {
			throw new IllegalArgumentException("현재 사용자의 조리 기록이 아닌 추천 근거가 포함되었습니다.");
		}
	}

	private String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String limitReason(String value) {
		String normalized = trimToNull(value);
		if (normalized == null || normalized.length() <= MAX_REASON_LENGTH) {
			return normalized;
		}
		return normalized.substring(0, MAX_REASON_LENGTH);
	}
}
