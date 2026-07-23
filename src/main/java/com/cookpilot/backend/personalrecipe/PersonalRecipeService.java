package com.cookpilot.backend.personalrecipe;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.recipe.RecipeEntity;
import com.cookpilot.backend.recipe.RecipeIngredientEntity;
import com.cookpilot.backend.recipe.RecipeIngredientRepository;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.recipe.RecipeStepEntity;
import com.cookpilot.backend.recipe.RecipeStepRepository;
import com.cookpilot.backend.user.UserService;

/**
 * 개인 레시피 버전(JPA 영속). 조정값은 관계형 diff 로 저장한다.
 *
 * 핵심 시맨틱:
 *  - diff 는 항상 원본 레시피 기준 누적 → 렌더링은 원본 + 해당 버전 diff 만으로 끝난다.
 *  - 파생(vN+1)은 부모 diff 를 복사한 뒤 수정한 결과를 저장하고 parent_version_id 로 계보를 남긴다.
 *  - 버전은 (user, recipe) 안에서 version_number 1, 2, 3… 으로 쌓이고 최신이 is_default 가 된다.
 */
@Service
public class PersonalRecipeService {

	private final PersonalRecipeVersionRepository versionRepository;
	private final PersonalIngredientAdjustmentRepository ingredientAdjustmentRepository;
	private final PersonalStepAdjustmentRepository stepAdjustmentRepository;
	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final RecipeStepRepository recipeStepRepository;
	private final UserService userService;

	public PersonalRecipeService(PersonalRecipeVersionRepository versionRepository,
			PersonalIngredientAdjustmentRepository ingredientAdjustmentRepository,
			PersonalStepAdjustmentRepository stepAdjustmentRepository,
			RecipeRepository recipeRepository,
			RecipeIngredientRepository recipeIngredientRepository,
			RecipeStepRepository recipeStepRepository,
			UserService userService) {
		this.versionRepository = versionRepository;
		this.ingredientAdjustmentRepository = ingredientAdjustmentRepository;
		this.stepAdjustmentRepository = stepAdjustmentRepository;
		this.recipeRepository = recipeRepository;
		this.recipeIngredientRepository = recipeIngredientRepository;
		this.recipeStepRepository = recipeStepRepository;
		this.userService = userService;
	}

	/**
	 * 리뷰 저장 시 v1..vN 을 생성한다. 조정값 구조화는 AI 미확정이라 diff 없이(빈 조정) 시작하고,
	 * 리뷰 원문은 summary 로 남긴다. AI 확정 후 이 지점에서 구조화된 diff 를 함께 저장한다.
	 */
	@Transactional
	public PersonalRecipeVersion createFromReview(UUID recipeId, UUID sourceReviewId, String comment,
			String nextTimeNote) {
		RecipeEntity recipe = recipeRepository.findById(recipeId)
				.orElseThrow(() -> new NotFoundException("레시피를 찾을 수 없습니다: " + recipeId));
		UUID userId = userService.getCurrentUser().id();

		int nextVersionNumber = nextVersionNumber(userId, recipeId);
		PersonalRecipeVersionEntity entity = new PersonalRecipeVersionEntity(
				userId, recipeId, nextVersionNumber,
				recipe.getTitle() + " - 내 버전 v" + nextVersionNumber,
				comment, sourceReviewId);
		promoteToDefault(userId, recipeId, entity);
		return PersonalRecipeVersion.from(versionRepository.save(entity));
	}

	/**
	 * 기존 버전에서 새 버전을 파생한다. 요청의 diff 는 원본 기준 누적 전체 집합이고,
	 * null 이면 부모 diff 를 그대로 복사한다.
	 */
	@Transactional
	public PersonalRecipeVersion derive(UUID parentVersionId, DeriveVersionRequest request) {
		PersonalRecipeVersionEntity parent = findEntity(parentVersionId);
		UUID userId = userService.getCurrentUser().id();
		UUID recipeId = parent.getRecipeId();

		List<IngredientAdjustment> ingredientAdjustments = request.ingredientAdjustments() != null
				? request.ingredientAdjustments()
				: ingredientAdjustmentRepository.findByPersonalVersionIdOrderBySortOrderAsc(parentVersionId)
						.stream().map(IngredientAdjustment::from).toList();
		List<StepAdjustment> stepAdjustments = request.stepAdjustments() != null
				? request.stepAdjustments()
				: stepAdjustmentRepository.findByPersonalVersionIdOrderBySortOrderAsc(parentVersionId)
						.stream().map(StepAdjustment::from).toList();

		validateIngredientAdjustments(recipeId, ingredientAdjustments);
		validateStepAdjustments(recipeId, stepAdjustments);

		int nextVersionNumber = nextVersionNumber(userId, recipeId);
		PersonalRecipeVersionEntity entity = new PersonalRecipeVersionEntity(
				userId, recipeId, nextVersionNumber,
				request.title() != null ? request.title() : parent.getTitle(),
				request.summary() != null ? request.summary() : parent.getSummary(),
				null);
		entity.setParentVersionId(parentVersionId);
		promoteToDefault(userId, recipeId, entity);
		PersonalRecipeVersionEntity saved = versionRepository.save(entity);

		ingredientAdjustmentRepository.saveAll(ingredientAdjustments.stream()
				.map(adj -> new PersonalIngredientAdjustmentEntity(saved.getId(),
						adj.originalIngredientId(), adj.type(), adj.name(), adj.amount(),
						adj.unit(), adj.required(), adj.sortOrder()))
				.toList());
		stepAdjustmentRepository.saveAll(stepAdjustments.stream()
				.map(adj -> new PersonalStepAdjustmentEntity(saved.getId(), adj.originalStepId(),
						adj.type(), adj.insertAfterStepIndex(), adj.sortOrder(), adj.instruction(),
						adj.timerSeconds(), adj.cautionNote()))
				.toList());

		return PersonalRecipeVersion.from(saved);
	}

	/** 상세: 메타 + 합성 결과(원본 + diff) + 원시 diff. */
	@Transactional(readOnly = true)
	public PersonalRecipeVersionDetail findDetailById(UUID versionId) {
		PersonalRecipeVersionEntity entity = findEntity(versionId);

		List<IngredientAdjustment> ingredientAdjustments = ingredientAdjustmentRepository
				.findByPersonalVersionIdOrderBySortOrderAsc(versionId)
				.stream().map(IngredientAdjustment::from).toList();
		List<StepAdjustment> stepAdjustments = stepAdjustmentRepository
				.findByPersonalVersionIdOrderBySortOrderAsc(versionId)
				.stream().map(StepAdjustment::from).toList();

		List<DiffComposer.OriginalIngredient> originalIngredients = recipeIngredientRepository
				.findByRecipeIdOrderBySortOrderAsc(entity.getRecipeId())
				.stream()
				.map(i -> new DiffComposer.OriginalIngredient(i.getId(), i.getName(), i.getAmount(),
						i.getUnit(), i.isRequired(), i.getSortOrder()))
				.toList();
		List<DiffComposer.OriginalStep> originalSteps = recipeStepRepository
				.findByRecipeIdOrderByStepIndexAsc(entity.getRecipeId())
				.stream()
				.map(s -> new DiffComposer.OriginalStep(s.getId(), s.getStepIndex(),
						s.getInstruction(), s.getTimerSeconds(), s.getCautionNote()))
				.toList();

		return new PersonalRecipeVersionDetail(
				PersonalRecipeVersion.from(entity),
				DiffComposer.composeIngredients(originalIngredients, ingredientAdjustments),
				DiffComposer.composeSteps(originalSteps, stepAdjustments),
				ingredientAdjustments,
				stepAdjustments);
	}

	@Transactional(readOnly = true)
	public PersonalRecipeVersion findById(UUID versionId) {
		return PersonalRecipeVersion.from(findEntity(versionId));
	}

	@Transactional(readOnly = true)
	public List<PersonalRecipeVersion> findByRecipe(UUID recipeId) {
		UUID userId = userService.getCurrentUser().id();
		return versionRepository.findByUserIdAndRecipeIdOrderByVersionNumberDesc(userId, recipeId)
				.stream().map(PersonalRecipeVersion::from).toList();
	}

	@Transactional(readOnly = true)
	public Optional<PersonalRecipeVersion> findLatestByRecipe(UUID recipeId) {
		UUID userId = userService.getCurrentUser().id();
		return versionRepository.findByUserIdAndRecipeIdOrderByVersionNumberDesc(userId, recipeId)
				.stream().findFirst().map(PersonalRecipeVersion::from);
	}

	private PersonalRecipeVersionEntity findEntity(UUID versionId) {
		return versionRepository.findById(versionId)
				.orElseThrow(() -> new NotFoundException("개인 레시피 버전을 찾을 수 없습니다: " + versionId));
	}

	private int nextVersionNumber(UUID userId, UUID recipeId) {
		return versionRepository.findByUserIdAndRecipeIdOrderByVersionNumberDesc(userId, recipeId)
				.stream().findFirst()
				.map(v -> v.getVersionNumber() + 1)
				.orElse(1);
	}

	/** 최신 버전이 다음 조리 때 기본 제공(is_default)된다. 이전 기본은 내린다. */
	private void promoteToDefault(UUID userId, UUID recipeId, PersonalRecipeVersionEntity newVersion) {
		versionRepository.findByUserIdAndRecipeIdAndIsDefaultTrue(userId, recipeId)
				.ifPresent(previous -> previous.setDefault(false));
		newVersion.setDefault(true);
	}

	private void validateIngredientAdjustments(UUID recipeId, List<IngredientAdjustment> adjustments) {
		Set<UUID> originalIds = new HashSet<>();
		recipeIngredientRepository.findByRecipeIdOrderBySortOrderAsc(recipeId)
				.forEach(i -> originalIds.add(i.getId()));
		for (IngredientAdjustment adj : adjustments) {
			if (adj.type() == null) {
				throw new IllegalArgumentException("재료 조정에 type은 필수입니다.");
			}
			if (adj.type() == AdjustmentType.ADD) {
				if (adj.originalIngredientId() != null) {
					throw new IllegalArgumentException("ADD 재료 조정은 원본 재료를 참조할 수 없습니다.");
				}
				if (adj.name() == null || adj.name().isBlank()) {
					throw new IllegalArgumentException("ADD 재료 조정에 name은 필수입니다.");
				}
			} else {
				if (adj.originalIngredientId() == null) {
					throw new IllegalArgumentException(adj.type() + " 재료 조정에 원본 재료 참조는 필수입니다.");
				}
				if (!originalIds.contains(adj.originalIngredientId())) {
					throw new IllegalArgumentException(
							"이 레시피의 재료가 아닙니다: " + adj.originalIngredientId());
				}
			}
		}
	}

	private void validateStepAdjustments(UUID recipeId, List<StepAdjustment> adjustments) {
		Set<UUID> originalIds = new HashSet<>();
		recipeStepRepository.findByRecipeIdOrderByStepIndexAsc(recipeId)
				.forEach(s -> originalIds.add(s.getId()));
		for (StepAdjustment adj : adjustments) {
			if (adj.type() == null) {
				throw new IllegalArgumentException("단계 조정에 type은 필수입니다.");
			}
			if (adj.type() == AdjustmentType.ADD) {
				if (adj.originalStepId() != null) {
					throw new IllegalArgumentException("ADD 단계 조정은 원본 단계를 참조할 수 없습니다.");
				}
				if (adj.instruction() == null || adj.instruction().isBlank()) {
					throw new IllegalArgumentException("ADD 단계 조정에 instruction은 필수입니다.");
				}
				if (adj.insertAfterStepIndex() == null || adj.insertAfterStepIndex() < -1) {
					throw new IllegalArgumentException("ADD 단계 조정에 insertAfterStepIndex(-1 이상)는 필수입니다.");
				}
			} else {
				if (adj.originalStepId() == null) {
					throw new IllegalArgumentException(adj.type() + " 단계 조정에 원본 단계 참조는 필수입니다.");
				}
				if (!originalIds.contains(adj.originalStepId())) {
					throw new IllegalArgumentException("이 레시피의 단계가 아닙니다: " + adj.originalStepId());
				}
			}
		}
	}
}
