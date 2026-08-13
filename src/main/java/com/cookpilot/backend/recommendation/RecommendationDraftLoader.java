package com.cookpilot.backend.recommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
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
import com.cookpilot.backend.recommendation.feedback.RecommendationFeedbackService;
import com.cookpilot.backend.recommendation.profile.RecipeFlavorProfileEntity;
import com.cookpilot.backend.recommendation.profile.RecipeFlavorProfileRepository;
import com.cookpilot.backend.review.PostCookReviewEntity;
import com.cookpilot.backend.review.PostCookReviewRepository;
import lombok.RequiredArgsConstructor;

/**
 * 추천 후보(Draft) 산출에 필요한 조회를 전부 담당한다.
 *
 * 조회는 여기서 한 트랜잭션으로 끝내고, 설명 생성(외부 HTTP)은
 * {@link RecommendationService} 가 트랜잭션 밖에서 호출한다. 그래야 Gemini 응답을
 * 기다리는 동안 DB 커넥션을 붙잡지 않는다. 판정 규칙 자체는
 * {@link RecommendationRuleEngine}(순수 함수) 가 갖는다.
 */
@Component
@RequiredArgsConstructor
class RecommendationDraftLoader {

	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final RecipeFlavorProfileRepository profileRepository;
	private final PersonalRecipeVersionRepository versionRepository;
	private final PersonalIngredientAdjustmentRepository adjustmentRepository;
	private final PostCookReviewRepository reviewRepository;
	private final RecommendationFeedbackService feedbackService;

	/** 대상 레시피 제목 + 확정된 추천 후보. 후보가 없으면 drafts 가 빈 목록이다. */
	record DraftBundle(String targetRecipeTitle, List<RecommendationRuleEngine.Draft> drafts) {

		static DraftBundle empty(String title) {
			return new DraftBundle(title, List.of());
		}
	}

	@Transactional(readOnly = true)
	DraftBundle loadDrafts(UUID userId, UUID recipeId) {
		RecipeEntity targetRecipe = recipeRepository.findById(recipeId)
				.orElseThrow(() -> new NotFoundException(
						"레시피를 찾을 수 없습니다: " + recipeId));
		String targetTitle = targetRecipe.getTitle();

		RecipeFlavorProfileEntity targetProfile = profileRepository.findById(recipeId)
				.orElse(null);
		if (targetProfile == null) {
			return DraftBundle.empty(targetTitle);
		}
		RecommendationRuleEngine.FlavorProfile targetFlavor = toFlavorProfile(targetProfile);

		Map<String, RecipeIngredientEntity> targetIngredientsByName =
				usableTargetIngredients(recipeId);
		if (targetIngredientsByName.isEmpty()) {
			return DraftBundle.empty(targetTitle);
		}

		List<ReviewExecution> reviewExecutions = reviewExecutions(userId);
		if (reviewExecutions.isEmpty()) {
			return DraftBundle.empty(targetTitle);
		}

		Map<RecommendationRuleEngine.IngredientSnapshot,
				List<RecommendationRuleEngine.EvidenceSample>> evidence = collectEvidence(
						recipeId, targetFlavor, targetIngredientsByName, reviewExecutions);

		Map<UUID, Instant> rejectionByIngredient =
				feedbackService.latestRejectionByIngredient(userId, recipeId);
		List<RecommendationRuleEngine.Draft> drafts = evidence.entrySet().stream()
				.map(entry -> RecommendationRuleEngine.draft(entry.getKey(), entry.getValue()))
				.filter(Objects::nonNull)
				.filter(draft -> !RecommendationRuleEngine.isRejectedWithoutNewEvidence(
						rejectionByIngredient.get(draft.targetIngredient().id()),
						draft.evidence()))
				.sorted(Comparator.comparing(
						RecommendationRuleEngine.Draft::confidence).reversed())
				.limit(RecommendationRuleEngine.MAX_RECOMMENDATIONS)
				.toList();
		return new DraftBundle(targetTitle, drafts);
	}

	/**
	 * 추천 대상이 될 수 있는 재료만 정규화한 이름으로 색인한다.
	 *
	 * 양·단위가 없으면 "몇 g 로 바꾸라"는 추천 자체가 불가능하므로 여기서 걸러낸다.
	 */
	private Map<String, RecipeIngredientEntity> usableTargetIngredients(UUID recipeId) {
		Map<String, RecipeIngredientEntity> byName = new LinkedHashMap<>();
		recipeIngredientRepository.findByRecipeIdOrderBySortOrderAsc(recipeId).stream()
				.filter(item -> item.getAmount() != null
						&& item.getAmount().signum() > 0
						&& item.getUnit() != null
						&& !item.getUnit().isBlank())
				.forEach(item -> byName.put(
						RecommendationRuleEngine.normalizeName(item.getName()), item));
		return byName;
	}

	/**
	 * 만족도 높은 조리 기록 중 개인 버전이 붙은 것만 추린다.
	 *
	 * 그 조리에서 새로 만든 버전이 있으면 그것을, 없으면 그때 사용한 버전을 쓴다.
	 */
	private List<ReviewExecution> reviewExecutions(UUID userId) {
		List<PostCookReviewEntity> positiveReviews = reviewRepository
				.findTop100ByUserIdAndRatingGreaterThanEqualOrderByCookedAtDescCreatedAtDesc(
						userId, 4);
		if (positiveReviews.isEmpty()) {
			return List.of();
		}
		Map<UUID, PersonalRecipeVersionEntity> createdVersionByReviewId = indexBy(
				versionRepository.findByUserIdAndSourceReviewIdIn(
						userId,
						positiveReviews.stream()
								.map(PostCookReviewEntity::getId)
								.toList()),
				PersonalRecipeVersionEntity::getSourceReviewId);
		Set<UUID> sourceVersionIds = positiveReviews.stream()
				.map(PostCookReviewEntity::getSourcePersonalVersionId)
				.filter(Objects::nonNull)
				.collect(Collectors.toCollection(HashSet::new));
		Map<UUID, PersonalRecipeVersionEntity> sourceVersionsById = sourceVersionIds.isEmpty()
				? Map.of()
				: indexBy(
						versionRepository.findByUserIdAndIdIn(userId, sourceVersionIds),
						PersonalRecipeVersionEntity::getId);
		return positiveReviews.stream()
				.map(review -> new ReviewExecution(
						review,
						createdVersionByReviewId.getOrDefault(
								review.getId(),
								sourceVersionsById.get(
										review.getSourcePersonalVersionId()))))
				.filter(item -> item.version() != null)
				.toList();
	}

	/**
	 * 양 조정 이력을 대상 레시피의 재료별 근거로 모은다.
	 *
	 * 프로파일 유사도는 근거 레시피 단위로만 달라지므로 레시피별로 한 번만 계산한다.
	 */
	private Map<RecommendationRuleEngine.IngredientSnapshot,
			List<RecommendationRuleEngine.EvidenceSample>> collectEvidence(
					UUID recipeId,
					RecommendationRuleEngine.FlavorProfile targetFlavor,
					Map<String, RecipeIngredientEntity> targetIngredientsByName,
					List<ReviewExecution> reviewExecutions) {
		Set<UUID> sourceRecipeIds = reviewExecutions.stream()
				.map(item -> item.version().getRecipeId())
				.collect(Collectors.toCollection(HashSet::new));
		Map<UUID, RecommendationRuleEngine.FlavorProfile> flavorsByRecipe = new HashMap<>();
		profileRepository.findAllById(sourceRecipeIds).forEach(profile ->
				flavorsByRecipe.put(profile.getRecipeId(), toFlavorProfile(profile)));
		Map<UUID, RecipeEntity> recipesById = indexBy(
				recipeRepository.findAllById(sourceRecipeIds), RecipeEntity::getId);

		Map<UUID, List<ReviewExecution>> executionsByVersionId = new HashMap<>();
		reviewExecutions.forEach(item -> executionsByVersionId
				.computeIfAbsent(item.version().getId(), ignored -> new ArrayList<>())
				.add(item));

		List<PersonalIngredientAdjustmentEntity> adjustments = adjustmentRepository
				.findByPersonalVersionIdInOrderByPersonalVersionIdAscSortOrderAsc(
						executionsByVersionId.keySet());
		Map<UUID, RecipeIngredientEntity> originalsById = indexBy(
				recipeIngredientRepository.findAllById(adjustments.stream()
						.map(PersonalIngredientAdjustmentEntity::getOriginalIngredientId)
						.filter(Objects::nonNull)
						.collect(Collectors.toCollection(HashSet::new))),
				RecipeIngredientEntity::getId);

		Map<UUID, Double> similarityByRecipe = new HashMap<>();
		Map<RecommendationRuleEngine.IngredientSnapshot,
				List<RecommendationRuleEngine.EvidenceSample>> evidence = new LinkedHashMap<>();
		for (PersonalIngredientAdjustmentEntity adjustment : adjustments) {
			List<ReviewExecution> executions =
					executionsByVersionId.get(adjustment.getPersonalVersionId());
			if (executions == null || !isAmountAdjustment(adjustment)) {
				continue;
			}
			UUID sourceRecipeId = executions.getFirst().version().getRecipeId();

			double similarity = similarityByRecipe.computeIfAbsent(
					sourceRecipeId,
					id -> RecommendationRuleEngine.profileSimilarity(
							targetFlavor, flavorsByRecipe.get(id), recipeId.equals(id)));
			if (!RecommendationRuleEngine.passesSimilarity(similarity)) {
				continue;
			}

			RecipeIngredientEntity original =
					originalsById.get(adjustment.getOriginalIngredientId());
			if (original == null || original.getAmount() == null
					|| original.getAmount().signum() <= 0) {
				continue;
			}
			RecipeIngredientEntity target = targetIngredientsByName.get(
					RecommendationRuleEngine.normalizeName(original.getName()));
			if (target == null || !RecommendationRuleEngine.sameText(
					target.getUnit(), original.getUnit())) {
				continue;
			}

			BigDecimal ratio = adjustment.getAmount()
					.divide(original.getAmount(), 6, RoundingMode.HALF_UP);
			if (!RecommendationRuleEngine.isUsableRatio(ratio.doubleValue())) {
				continue;
			}

			RecipeEntity sourceRecipe = recipesById.get(sourceRecipeId);
			List<RecommendationRuleEngine.EvidenceSample> samples =
					evidence.computeIfAbsent(toSnapshot(target), ignored -> new ArrayList<>());
			for (ReviewExecution execution : executions) {
				PostCookReviewEntity review = execution.review();
				samples.add(new RecommendationRuleEngine.EvidenceSample(
						ratio,
						similarity,
						new RecommendationEvidence(
								review.getId(),
								review.getRecipeId(),
								sourceRecipe != null ? sourceRecipe.getTitle() : "과거 요리",
								review.getCookedAt(),
								review.getRating())));
			}
		}
		return evidence;
	}

	private RecommendationRuleEngine.IngredientSnapshot toSnapshot(
			RecipeIngredientEntity ingredient) {
		return new RecommendationRuleEngine.IngredientSnapshot(
				ingredient.getId(),
				ingredient.getName(),
				ingredient.getAmount(),
				ingredient.getUnit());
	}

	private RecommendationRuleEngine.FlavorProfile toFlavorProfile(
			RecipeFlavorProfileEntity profile) {
		if (profile == null) {
			return null;
		}
		return new RecommendationRuleEngine.FlavorProfile(
				profile.getCuisine(),
				profile.getDishType(),
				profile.getCookingMethods(),
				profile.getSauceBases());
	}

	/** 이름 변경 없이 양만 바꾼 조정만 추천 근거가 된다. */
	private boolean isAmountAdjustment(PersonalIngredientAdjustmentEntity adjustment) {
		return adjustment.getAdjustmentType() == AdjustmentType.MODIFY
				&& adjustment.getAmount() != null
				&& adjustment.getName() == null
				&& adjustment.getUnit() == null;
	}

	/** 조회 결과를 키별로 묶는다. 다섯 군데에서 같은 모양이라 하나로 모았다. */
	private static <T> Map<UUID, T> indexBy(
			Iterable<T> rows, Function<T, UUID> keyExtractor) {
		Map<UUID, T> result = new HashMap<>();
		rows.forEach(row -> result.put(keyExtractor.apply(row), row));
		return result;
	}

	private record ReviewExecution(
			PostCookReviewEntity review,
			PersonalRecipeVersionEntity version
	) {
	}
}
