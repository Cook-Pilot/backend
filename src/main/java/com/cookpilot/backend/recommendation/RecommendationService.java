package com.cookpilot.backend.recommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.personalrecipe.AdjustmentType;
import com.cookpilot.backend.personalrecipe.PersonalIngredientAdjustmentEntity;
import com.cookpilot.backend.personalrecipe.PersonalIngredientAdjustmentRepository;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionEntity;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionRepository;
import com.cookpilot.backend.recipe.RecipeEntity;
import com.cookpilot.backend.recipe.RecipeIngredientEntity;
import com.cookpilot.backend.recipe.RecipeIngredientRepository;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.review.PostCookReviewEntity;
import com.cookpilot.backend.review.PostCookReviewRepository;
import com.cookpilot.backend.user.UserService;

@Service
public class RecommendationService {

	private static final int MIN_EVIDENCE_COUNT = 2;
	private static final int MAX_RECOMMENDATIONS = 3;
	private static final int MAX_EVIDENCE_PER_RECOMMENDATION = 5;
	private static final double MIN_PROFILE_SIMILARITY = 0.60;
	private static final double MIN_MEANINGFUL_RATIO_DIFFERENCE = 0.05;

	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final RecipeFlavorProfileRepository profileRepository;
	private final PersonalRecipeVersionRepository versionRepository;
	private final PersonalIngredientAdjustmentRepository adjustmentRepository;
	private final PostCookReviewRepository reviewRepository;
	private final RecommendationFeedbackRepository feedbackRepository;
	private final RecommendationExplanationService explanationService;
	private final UserService userService;

	public RecommendationService(
			RecipeRepository recipeRepository,
			RecipeIngredientRepository recipeIngredientRepository,
			RecipeFlavorProfileRepository profileRepository,
			PersonalRecipeVersionRepository versionRepository,
			PersonalIngredientAdjustmentRepository adjustmentRepository,
			PostCookReviewRepository reviewRepository,
			RecommendationFeedbackRepository feedbackRepository,
			RecommendationExplanationService explanationService,
			UserService userService) {
		this.recipeRepository = recipeRepository;
		this.recipeIngredientRepository = recipeIngredientRepository;
		this.profileRepository = profileRepository;
		this.versionRepository = versionRepository;
		this.adjustmentRepository = adjustmentRepository;
		this.reviewRepository = reviewRepository;
		this.feedbackRepository = feedbackRepository;
		this.explanationService = explanationService;
		this.userService = userService;
	}

	@Transactional(readOnly = true)
	public NextCookRecommendationResponse recommend(UUID recipeId) {
		UUID userId = userService.getCurrentUser().id();
		RecipeEntity targetRecipe = recipeRepository.findById(recipeId)
				.orElseThrow(() -> new NotFoundException(
						"레시피를 찾을 수 없습니다: " + recipeId));
		RecipeFlavorProfileEntity targetProfile = profileRepository.findById(recipeId)
				.orElse(null);
		if (targetProfile == null) {
			return new NextCookRecommendationResponse(recipeId, Instant.now(), List.of());
		}

		List<RecipeIngredientEntity> targetIngredients =
				recipeIngredientRepository.findByRecipeIdOrderBySortOrderAsc(recipeId);
		Map<String, RecipeIngredientEntity> targetIngredientsByName = new LinkedHashMap<>();
		targetIngredients.stream()
				.filter(item -> item.getAmount() != null
						&& item.getAmount().signum() > 0
						&& item.getUnit() != null
						&& !item.getUnit().isBlank())
				.forEach(item -> targetIngredientsByName.put(
						normalizeName(item.getName()), item));
		if (targetIngredientsByName.isEmpty()) {
			return new NextCookRecommendationResponse(recipeId, Instant.now(), List.of());
		}

		List<PostCookReviewEntity> positiveReviews = reviewRepository
				.findTop100ByUserIdAndRatingGreaterThanEqualOrderByCookedAtDescCreatedAtDesc(
						userId, 4);
		if (positiveReviews.isEmpty()) {
			return new NextCookRecommendationResponse(recipeId, Instant.now(), List.of());
		}

		Map<UUID, PersonalRecipeVersionEntity> createdVersionByReviewId =
				mapVersionsCreatedByReview(
						userId,
						positiveReviews.stream()
								.map(PostCookReviewEntity::getId)
								.toList());
		Map<UUID, PersonalRecipeVersionEntity> sourceVersionsById =
				mapSourceVersions(
						userId,
						positiveReviews.stream()
								.map(PostCookReviewEntity::getSourcePersonalVersionId)
								.filter(java.util.Objects::nonNull)
								.toList());
		List<ReviewExecution> reviewExecutions = positiveReviews.stream()
				.map(review -> new ReviewExecution(
						review,
						createdVersionByReviewId.getOrDefault(
								review.getId(),
								sourceVersionsById.get(
										review.getSourcePersonalVersionId()))))
				.filter(item -> item.version() != null)
				.toList();
		if (reviewExecutions.isEmpty()) {
			return new NextCookRecommendationResponse(recipeId, Instant.now(), List.of());
		}

		Map<UUID, RecipeFlavorProfileEntity> profilesByRecipe = mapProfiles(
				reviewExecutions.stream()
						.map(item -> item.version().getRecipeId())
						.toList());
		Map<UUID, RecipeEntity> recipesById = mapRecipes(
				reviewExecutions.stream()
						.map(item -> item.version().getRecipeId())
						.toList());
		Map<UUID, List<ReviewExecution>> executionsByVersionId = new HashMap<>();
		reviewExecutions.forEach(item -> executionsByVersionId
				.computeIfAbsent(item.version().getId(), ignored -> new ArrayList<>())
				.add(item));

		List<PersonalIngredientAdjustmentEntity> adjustments = adjustmentRepository
				.findByPersonalVersionIdInOrderByPersonalVersionIdAscSortOrderAsc(
						executionsByVersionId.keySet());
		Map<UUID, RecipeIngredientEntity> originalsById = mapOriginalIngredients(
				adjustments.stream()
						.map(PersonalIngredientAdjustmentEntity::getOriginalIngredientId)
						.filter(java.util.Objects::nonNull)
						.toList());

		Map<UUID, List<EvidenceSample>> evidenceByTargetIngredient = new LinkedHashMap<>();
		for (PersonalIngredientAdjustmentEntity adjustment : adjustments) {
			List<ReviewExecution> executions =
					executionsByVersionId.get(adjustment.getPersonalVersionId());
			if (executions == null || !isAmountAdjustment(adjustment)) {
				continue;
			}
			PersonalRecipeVersionEntity version = executions.getFirst().version();

			RecipeFlavorProfileEntity sourceProfile =
					profilesByRecipe.get(version.getRecipeId());
			double similarity = profileSimilarity(
					targetProfile, sourceProfile, recipeId.equals(version.getRecipeId()));
			if (similarity < MIN_PROFILE_SIMILARITY) {
				continue;
			}

			RecipeIngredientEntity original =
					originalsById.get(adjustment.getOriginalIngredientId());
			if (original == null || original.getAmount() == null
					|| original.getAmount().signum() <= 0) {
				continue;
			}
			RecipeIngredientEntity target =
					targetIngredientsByName.get(normalizeName(original.getName()));
			if (target == null || !sameText(target.getUnit(), original.getUnit())) {
				continue;
			}

			BigDecimal ratio = adjustment.getAmount()
					.divide(original.getAmount(), 6, RoundingMode.HALF_UP);
			double ratioValue = ratio.doubleValue();
			if (ratioValue < 0.25 || ratioValue > 2.0
					|| Math.abs(ratioValue - 1.0) < MIN_MEANINGFUL_RATIO_DIFFERENCE) {
				continue;
			}

			RecipeEntity sourceRecipe = recipesById.get(version.getRecipeId());
			for (ReviewExecution execution : executions) {
				PostCookReviewEntity review = execution.review();
				RecommendationEvidence evidence = new RecommendationEvidence(
						review.getId(),
						review.getRecipeId(),
						sourceRecipe != null ? sourceRecipe.getTitle() : "과거 요리",
						review.getCookedAt(),
						review.getRating());
				evidenceByTargetIngredient
						.computeIfAbsent(target.getId(), ignored -> new ArrayList<>())
						.add(new EvidenceSample(ratio, similarity, evidence));
			}
		}

		Map<UUID, RecommendationFeedbackEntity> latestFeedbackByIngredient =
				new HashMap<>();
		feedbackRepository.findByUserIdAndRecipeIdOrderByCreatedAtDesc(userId, recipeId)
				.forEach(item -> {
					if (item.getOriginalIngredientId() != null) {
						latestFeedbackByIngredient.putIfAbsent(
								item.getOriginalIngredientId(), item);
					}
				});
		List<RecommendationDraft> drafts = evidenceByTargetIngredient.entrySet().stream()
				.map(entry -> draft(
						findTargetIngredient(targetIngredients, entry.getKey()),
						entry.getValue()))
				.filter(java.util.Objects::nonNull)
				.filter(draft -> !isRejectedWithoutNewEvidence(
						latestFeedbackByIngredient.get(
								draft.targetIngredient().getId()),
						draft))
				.sorted(Comparator.comparing(RecommendationDraft::confidence).reversed())
				.limit(MAX_RECOMMENDATIONS)
				.toList();

		List<RecommendationExplanationContext> contexts = drafts.stream()
				.map(draft -> new RecommendationExplanationContext(
						targetRecipe.getTitle(),
						draft.targetIngredient().getName(),
						draft.targetIngredient().getAmount(),
						draft.suggestedAmount(),
						draft.targetIngredient().getUnit(),
						draft.changePercent(),
						draft.evidence()))
				.toList();
		List<RecommendationExplanationService.Explanation> explanations =
				explanationService.explainAll(contexts);
		List<NextCookRecommendation> recommendations = new ArrayList<>();
		for (int index = 0; index < drafts.size(); index++) {
			recommendations.add(toRecommendation(
					drafts.get(index), explanations.get(index)));
		}
		return new NextCookRecommendationResponse(recipeId, Instant.now(), recommendations);
	}

	@Transactional
	public RecommendationFeedbackResponse recordFeedback(
			UUID recipeId,
			UUID recommendationId,
			SubmitRecommendationFeedbackRequest request) {
		validateFeedbackRequest(recipeId, request);
		UUID userId = userService.lockCurrentUser().id();
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
				new RecommendationFeedbackEntity(
						recommendationId,
						userId,
						recipeId,
						request.originalIngredientId(),
						request.decision(),
						request.originalAmount(),
						request.suggestedAmount(),
						appliedAmount,
						trimToNull(request.unit()),
						limitText(request.reason(), 500),
						request.explanationSource(),
						trimToNull(request.model()),
						request.promptVersion().trim(),
						request.evidenceReviewIds()));
		return RecommendationFeedbackResponse.from(saved);
	}

	private RecommendationDraft draft(
			RecipeIngredientEntity target,
			List<EvidenceSample> samples) {
		Map<UUID, EvidenceSample> uniqueByReview = new LinkedHashMap<>();
		samples.stream()
				.sorted(Comparator.comparing(
						(EvidenceSample sample) -> sample.evidence().cookedAt()).reversed())
				.forEach(sample -> uniqueByReview.putIfAbsent(
						sample.evidence().reviewId(), sample));
		List<EvidenceSample> uniqueSamples = List.copyOf(uniqueByReview.values());
		if (uniqueSamples.size() < MIN_EVIDENCE_COUNT) {
			return null;
		}

		double totalWeight = uniqueSamples.stream()
				.mapToDouble(EvidenceSample::profileSimilarity)
				.sum();
		double weightedRatio = uniqueSamples.stream()
				.mapToDouble(sample ->
						sample.ratio().doubleValue() * sample.profileSimilarity())
				.sum() / totalWeight;
		if (Math.abs(weightedRatio - 1.0) < MIN_MEANINGFUL_RATIO_DIFFERENCE) {
			return null;
		}

		BigDecimal suggestedAmount = target.getAmount()
				.multiply(BigDecimal.valueOf(weightedRatio))
				.setScale(2, RoundingMode.HALF_UP)
				.stripTrailingZeros();
		int changePercent = (int) Math.round((weightedRatio - 1.0) * 100);
		double averageSimilarity = totalWeight / uniqueSamples.size();
		BigDecimal confidence = BigDecimal.valueOf(Math.min(
						0.95,
						0.45 + uniqueSamples.size() * 0.10 + averageSimilarity * 0.20))
				.setScale(2, RoundingMode.HALF_UP);
		List<RecommendationEvidence> evidence = uniqueSamples.stream()
				.map(EvidenceSample::evidence)
				.limit(MAX_EVIDENCE_PER_RECOMMENDATION)
				.toList();
		return new RecommendationDraft(
				target,
				suggestedAmount,
				changePercent,
				confidence,
				evidence);
	}

	private NextCookRecommendation toRecommendation(
			RecommendationDraft draft,
			RecommendationExplanationService.Explanation explanation) {
		return new NextCookRecommendation(
				UUID.randomUUID(),
				"INGREDIENT_AMOUNT",
				draft.targetIngredient().getId(),
				draft.targetIngredient().getName(),
				draft.targetIngredient().getAmount(),
				draft.suggestedAmount(),
				draft.targetIngredient().getUnit(),
				draft.changePercent(),
				draft.confidence(),
				explanation.reason(),
				explanation.source(),
				explanation.model(),
				explanation.promptVersion(),
				draft.evidence());
	}

	private boolean isRejectedWithoutNewEvidence(
			RecommendationFeedbackEntity latest,
			RecommendationDraft draft) {
		if (latest == null || latest.getDecision() != RecommendationDecision.REJECTED) {
			return false;
		}
		Instant latestEvidenceAt = draft.evidence().stream()
				.map(RecommendationEvidence::cookedAt)
				.max(Comparator.naturalOrder())
				.orElse(Instant.MIN);
		return !latest.getCreatedAt().isBefore(latestEvidenceAt);
	}

	private void validateFeedbackRequest(
			UUID recipeId,
			SubmitRecommendationFeedbackRequest request) {
		if (request == null || request.originalIngredientId() == null
				|| request.decision() == null
				|| request.originalAmount() == null
				|| request.suggestedAmount() == null
				|| request.explanationSource() == null
				|| request.promptVersion() == null
				|| request.promptVersion().isBlank()) {
			throw new IllegalArgumentException("추천 피드백 필수값이 누락되었습니다.");
		}
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
		if (!Set.of("GEMINI", "FALLBACK").contains(request.explanationSource())) {
			throw new IllegalArgumentException("추천 설명 출처가 올바르지 않습니다.");
		}
		if (request.evidenceReviewIds().size() > 10) {
			throw new IllegalArgumentException("추천 근거는 최대 10개까지 저장할 수 있습니다.");
		}
		Set<UUID> evidenceReviewIds = new HashSet<>(request.evidenceReviewIds());
		if (reviewRepository.findByUserIdAndIdIn(
				userService.getCurrentUser().id(), evidenceReviewIds).size()
				!= evidenceReviewIds.size()) {
			throw new IllegalArgumentException("현재 사용자의 조리 기록이 아닌 추천 근거가 포함되었습니다.");
		}
	}

	private boolean isAmountAdjustment(
			PersonalIngredientAdjustmentEntity adjustment) {
		return adjustment.getAdjustmentType() == AdjustmentType.MODIFY
				&& adjustment.getAmount() != null
				&& adjustment.getName() == null
				&& adjustment.getUnit() == null;
	}

	private double profileSimilarity(
			RecipeFlavorProfileEntity target,
			RecipeFlavorProfileEntity source,
			boolean sameRecipe) {
		if (source == null) {
			return 0;
		}
		if (sameRecipe) {
			return 1;
		}
		double score = 0;
		if (sameText(target.getCuisine(), source.getCuisine())) {
			score += 0.25;
		}
		if (sameText(target.getDishType(), source.getDishType())) {
			score += 0.15;
		}
		if (intersects(target.getCookingMethods(), source.getCookingMethods())) {
			score += 0.15;
		}
		if (intersects(target.getSauceBases(), source.getSauceBases())) {
			score += 0.45;
		}
		return Math.min(1, score);
	}

	private boolean intersects(Collection<String> left, Collection<String> right) {
		Set<String> normalized = new HashSet<>();
		left.forEach(value -> normalized.add(value.toUpperCase(Locale.ROOT)));
		return right.stream()
				.map(value -> value.toUpperCase(Locale.ROOT))
				.anyMatch(normalized::contains);
	}

	private Map<UUID, PersonalRecipeVersionEntity> mapVersionsCreatedByReview(
			UUID userId, Collection<UUID> reviewIds) {
		Map<UUID, PersonalRecipeVersionEntity> result = new HashMap<>();
		versionRepository.findByUserIdAndSourceReviewIdIn(userId, reviewIds)
				.forEach(version -> result.put(
						version.getSourceReviewId(), version));
		return result;
	}

	private Map<UUID, PersonalRecipeVersionEntity> mapSourceVersions(
			UUID userId, Collection<UUID> versionIds) {
		Map<UUID, PersonalRecipeVersionEntity> result = new HashMap<>();
		if (versionIds.isEmpty()) {
			return result;
		}
		versionRepository.findByUserIdAndIdIn(userId, versionIds)
				.forEach(version -> result.put(version.getId(), version));
		return result;
	}

	private Map<UUID, RecipeFlavorProfileEntity> mapProfiles(
			Collection<UUID> recipeIds) {
		Map<UUID, RecipeFlavorProfileEntity> result = new HashMap<>();
		profileRepository.findAllById(new HashSet<>(recipeIds))
				.forEach(profile -> result.put(profile.getRecipeId(), profile));
		return result;
	}

	private Map<UUID, RecipeEntity> mapRecipes(Collection<UUID> recipeIds) {
		Map<UUID, RecipeEntity> result = new HashMap<>();
		recipeRepository.findAllById(new HashSet<>(recipeIds))
				.forEach(recipe -> result.put(recipe.getId(), recipe));
		return result;
	}

	private Map<UUID, RecipeIngredientEntity> mapOriginalIngredients(
			Collection<UUID> ingredientIds) {
		Map<UUID, RecipeIngredientEntity> result = new HashMap<>();
		recipeIngredientRepository.findAllById(new HashSet<>(ingredientIds))
				.forEach(ingredient -> result.put(ingredient.getId(), ingredient));
		return result;
	}

	private RecipeIngredientEntity findTargetIngredient(
			List<RecipeIngredientEntity> ingredients, UUID ingredientId) {
		return ingredients.stream()
				.filter(item -> item.getId().equals(ingredientId))
				.findFirst()
				.orElseThrow();
	}

	private String normalizeName(String value) {
		return value == null
				? ""
				: value.replaceAll("\\s+", "").toLowerCase(Locale.KOREAN);
	}

	private boolean sameText(String left, String right) {
		return left == null ? right == null : right != null && left.equalsIgnoreCase(right);
	}

	private String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String limitText(String value, int limit) {
		String normalized = trimToNull(value);
		if (normalized == null || normalized.length() <= limit) {
			return normalized;
		}
		return normalized.substring(0, limit);
	}

	private record EvidenceSample(
			BigDecimal ratio,
			double profileSimilarity,
			RecommendationEvidence evidence
	) {
	}

	private record ReviewExecution(
			PostCookReviewEntity review,
			PersonalRecipeVersionEntity version
	) {
	}

	private record RecommendationDraft(
			RecipeIngredientEntity targetIngredient,
			BigDecimal suggestedAmount,
			int changePercent,
			BigDecimal confidence,
			List<RecommendationEvidence> evidence
	) {
	}
}
