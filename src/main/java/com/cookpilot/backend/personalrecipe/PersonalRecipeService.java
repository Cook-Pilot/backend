package com.cookpilot.backend.personalrecipe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *  - 버전은 (user, recipe) 안에서 version_number 1, 2, 3… 으로 쌓인다.
 *  - 원본과 개인 버전 중 무엇을 쓸지는 사용자가 조리 전에 직접 선택한다.
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

	/** 실제 실행 결과가 원본과 다를 때만 원본 기준 누적 diff 버전을 만든다. */
	@Transactional
	public Optional<PersonalRecipeVersion> createFromExecution(
			UUID recipeId, UUID sourceReviewId, ExecutedRecipe execution) {
		RecipeEntity recipe = recipeRepository.findById(recipeId)
				.orElseThrow(() -> new NotFoundException("레시피를 찾을 수 없습니다: " + recipeId));
		UUID userId = userService.getCurrentUser().id();

		BigDecimal targetServings = execution.targetServings() != null
				? execution.targetServings()
				: recipe.getBaseServings();
		if (targetServings.signum() <= 0) {
			throw new IllegalArgumentException("targetServings는 0보다 커야 합니다.");
		}
		validateSourceVersion(userId, recipeId, execution.sourcePersonalVersionId());

		// 스냅샷 자체가 없으면(별점·메모만 전송) 실행 변경 판정이 불가능하므로 버전을 만들지 않는다.
		// 소스 버전이 지정된 경우에도 마찬가지 — 빈 diff 와 소스 diff 를 비교하면 원본과 동일한
		// 빈 버전이 생기는 오판을 막는다.
		boolean hasIngredientSnapshot = execution.ingredients() != null
				&& !execution.ingredients().isEmpty();
		boolean hasStepSnapshot = execution.steps() != null && !execution.steps().isEmpty();
		if (!hasIngredientSnapshot && !hasStepSnapshot) {
			return Optional.empty();
		}

		List<IngredientAdjustment> ingredientAdjustments =
				buildIngredientAdjustments(recipe, targetServings, execution.ingredients());
		List<StepAdjustment> stepAdjustments =
				buildStepAdjustments(recipeId, execution.steps());
		// 조정 0개 = 원본과 동일한 실행. 소스 diff 와 다르더라도(예: 개인 버전을 쓰다 원본으로
		// 돌아간 조리) 원본과 같은 빈 버전을 만들 이유는 없다.
		if (ingredientAdjustments.isEmpty() && stepAdjustments.isEmpty()) {
			return Optional.empty();
		}
		List<IngredientAdjustment> sourceIngredientAdjustments =
				sourceIngredientAdjustments(execution.sourcePersonalVersionId());
		List<StepAdjustment> sourceStepAdjustments =
				sourceStepAdjustments(execution.sourcePersonalVersionId());
		if (sameIngredientAdjustments(ingredientAdjustments, sourceIngredientAdjustments)
				&& sameStepAdjustments(stepAdjustments, sourceStepAdjustments)) {
			return Optional.empty();
		}

		int nextVersionNumber = nextVersionNumber(userId, recipeId);
		PersonalRecipeVersionEntity entity = new PersonalRecipeVersionEntity(
				userId, recipeId, nextVersionNumber,
				recipe.getTitle() + " - 내 버전 v" + nextVersionNumber,
				buildSummary(ingredientAdjustments, stepAdjustments), sourceReviewId);
		entity.setParentVersionId(execution.sourcePersonalVersionId());
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
		return Optional.of(PersonalRecipeVersion.from(saved));
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
				.stream().limit(5).map(PersonalRecipeVersion::from).toList();
	}

	@Transactional(readOnly = true)
	public Optional<PersonalRecipeVersion> findLatestByRecipe(UUID recipeId) {
		UUID userId = userService.getCurrentUser().id();
		return versionRepository.findByUserIdAndRecipeIdOrderByVersionNumberDesc(userId, recipeId)
				.stream().findFirst().map(PersonalRecipeVersion::from);
	}

	@Transactional(readOnly = true)
	public Map<UUID, PersonalRecipeVersion> findLatestByRecipes(Collection<UUID> recipeIds) {
		if (recipeIds.isEmpty()) {
			return Map.of();
		}
		UUID userId = userService.getCurrentUser().id();
		Map<UUID, PersonalRecipeVersion> latestByRecipe = new HashMap<>();
		versionRepository.findByUserIdAndRecipeIdInOrderByVersionNumberDesc(userId, recipeIds)
				.forEach(entity -> latestByRecipe.putIfAbsent(
						entity.getRecipeId(), PersonalRecipeVersion.from(entity)));
		return Map.copyOf(latestByRecipe);
	}

	@Transactional(readOnly = true)
	public Optional<UUID> findCreatedVersionId(UUID sourceReviewId) {
		return versionRepository.findFirstBySourceReviewId(sourceReviewId)
				.map(PersonalRecipeVersionEntity::getId);
	}

	private PersonalRecipeVersionEntity findEntity(UUID versionId) {
		UUID userId = userService.getCurrentUser().id();
		return versionRepository.findByIdAndUserId(versionId, userId)
				.orElseThrow(() -> new NotFoundException("개인 레시피 버전을 찾을 수 없습니다: " + versionId));
	}

	private int nextVersionNumber(UUID userId, UUID recipeId) {
		return versionRepository.findByUserIdAndRecipeIdOrderByVersionNumberDesc(userId, recipeId)
				.stream().findFirst()
				.map(v -> v.getVersionNumber() + 1)
				.orElse(1);
	}

	private void validateSourceVersion(UUID userId, UUID recipeId, UUID sourceVersionId) {
		if (sourceVersionId == null) {
			return;
		}
		PersonalRecipeVersionEntity source = versionRepository.findByIdAndUserId(sourceVersionId, userId)
				.orElseThrow(() -> new NotFoundException(
						"개인 레시피 버전을 찾을 수 없습니다: " + sourceVersionId));
		if (!source.getRecipeId().equals(recipeId)) {
			throw new IllegalArgumentException("선택한 개인 버전이 현재 레시피에 속하지 않습니다.");
		}
	}

	private List<IngredientAdjustment> sourceIngredientAdjustments(UUID sourceVersionId) {
		if (sourceVersionId == null) {
			return List.of();
		}
		return ingredientAdjustmentRepository
				.findByPersonalVersionIdOrderBySortOrderAsc(sourceVersionId)
				.stream().map(IngredientAdjustment::from).toList();
	}

	private List<StepAdjustment> sourceStepAdjustments(UUID sourceVersionId) {
		if (sourceVersionId == null) {
			return List.of();
		}
		return stepAdjustmentRepository
				.findByPersonalVersionIdOrderBySortOrderAsc(sourceVersionId)
				.stream().map(StepAdjustment::from).toList();
	}

	private List<IngredientAdjustment> buildIngredientAdjustments(
			RecipeEntity recipe, BigDecimal targetServings,
			List<ExecutedRecipe.ExecutedIngredient> executedIngredients) {
		if (executedIngredients == null || executedIngredients.isEmpty()) {
			return List.of();
		}

		List<RecipeIngredientEntity> originals = recipeIngredientRepository
				.findByRecipeIdOrderBySortOrderAsc(recipe.getId());
		Map<UUID, RecipeIngredientEntity> originalsById = new LinkedHashMap<>();
		originals.forEach(item -> originalsById.put(item.getId(), item));

		Map<UUID, ExecutedRecipe.ExecutedIngredient> executedByOriginal = new HashMap<>();
		List<ExecutedRecipe.ExecutedIngredient> additions = new ArrayList<>();
		for (ExecutedRecipe.ExecutedIngredient item : executedIngredients) {
			if (item.originalIngredientId() == null) {
				additions.add(item);
				continue;
			}
			if (!originalsById.containsKey(item.originalIngredientId())) {
				throw new IllegalArgumentException(
						"이 레시피의 재료가 아닙니다: " + item.originalIngredientId());
			}
			if (executedByOriginal.put(item.originalIngredientId(), item) != null) {
				throw new IllegalArgumentException(
						"같은 원본 재료가 중복되었습니다: " + item.originalIngredientId());
			}
		}

		// 스냅샷은 원본 재료 전체를 커버해야 한다. 목록 부재를 암묵적 생략(REMOVE)으로
		// 해석하면 부분 페이로드가 조용히 재료 대량 삭제 버전을 만든다 — 생략은 omitted=true 로만 표현한다.
		for (RecipeIngredientEntity original : originals) {
			if (!executedByOriginal.containsKey(original.getId())) {
				throw new IllegalArgumentException(
						"실행 스냅샷에 원본 재료가 누락되었습니다(사용하지 않았다면 omitted=true로 보내세요): "
								+ original.getName());
			}
		}

		List<IngredientAdjustment> adjustments = new ArrayList<>();
		for (RecipeIngredientEntity original : originals) {
			ExecutedRecipe.ExecutedIngredient actual = executedByOriginal.get(original.getId());
			if (actual.omitted()) {
				adjustments.add(new IngredientAdjustment(
						original.getId(), AdjustmentType.REMOVE,
						null, null, null, null, original.getSortOrder()));
				continue;
			}
			validateIngredient(actual);
			if (original.getAmount() != null && actual.amount() == null) {
				throw new IllegalArgumentException(
						"MVP에서는 기존 재료의 양 제거를 지원하지 않습니다.");
			}
			if (trimToNull(original.getUnit()) != null && trimToNull(actual.unit()) == null) {
				throw new IllegalArgumentException(
						"MVP에서는 기존 재료의 단위 제거를 지원하지 않습니다.");
			}
			BigDecimal normalizedAmount = normalizeAmount(
					actual.amount(), recipe.getBaseServings(), targetServings);
			String changedName = sameText(original.getName(), actual.name()) ? null : actual.name().trim();
			BigDecimal changedAmount = sameAmount(original.getAmount(), normalizedAmount)
					? null : normalizedAmount;
			String changedUnit = sameText(original.getUnit(), actual.unit())
					? null : trimToNull(actual.unit());
			boolean required = actual.required() != null ? actual.required() : original.isRequired();
			Boolean changedRequired = original.isRequired() == required ? null : required;

			if (changedName != null || changedAmount != null
					|| changedUnit != null || changedRequired != null) {
				adjustments.add(new IngredientAdjustment(
						original.getId(), AdjustmentType.MODIFY,
						changedName, changedAmount, changedUnit, changedRequired,
						actual.sortOrder()));
			}
		}

		for (ExecutedRecipe.ExecutedIngredient addition : additions) {
			if (addition.omitted()) {
				continue;
			}
			validateIngredient(addition);
			adjustments.add(new IngredientAdjustment(
					null, AdjustmentType.ADD, addition.name().trim(),
					normalizeAmount(addition.amount(), recipe.getBaseServings(), targetServings),
					trimToNull(addition.unit()),
					addition.required() != null ? addition.required() : true,
					addition.sortOrder()));
		}
		return List.copyOf(adjustments);
	}

	private List<StepAdjustment> buildStepAdjustments(
			UUID recipeId, List<ExecutedRecipe.ExecutedStep> executedSteps) {
		if (executedSteps == null || executedSteps.isEmpty()) {
			return List.of();
		}

		List<RecipeStepEntity> originals = recipeStepRepository
				.findByRecipeIdOrderByStepIndexAsc(recipeId);
		Map<UUID, RecipeStepEntity> originalsById = new LinkedHashMap<>();
		originals.forEach(item -> originalsById.put(item.getId(), item));

		Map<UUID, ExecutedRecipe.ExecutedStep> executedByOriginal = new HashMap<>();
		List<ExecutedRecipe.ExecutedStep> ordered = executedSteps.stream()
				.sorted(java.util.Comparator.comparingInt(ExecutedRecipe.ExecutedStep::sortOrder))
				.toList();
		for (ExecutedRecipe.ExecutedStep item : ordered) {
			if (item.originalStepId() == null) {
				continue;
			}
			if (!originalsById.containsKey(item.originalStepId())) {
				throw new IllegalArgumentException(
						"이 레시피의 단계가 아닙니다: " + item.originalStepId());
			}
			if (executedByOriginal.put(item.originalStepId(), item) != null) {
				throw new IllegalArgumentException(
						"같은 원본 단계가 중복되었습니다: " + item.originalStepId());
			}
		}

		// 재료와 동일한 완전성 계약: 스냅샷은 원본 단계 전체를 커버해야 하고, 생략은 omitted=true 로만 표현한다.
		for (RecipeStepEntity original : originals) {
			if (!executedByOriginal.containsKey(original.getId())) {
				throw new IllegalArgumentException(
						"실행 스냅샷에 원본 단계가 누락되었습니다(수행하지 않았다면 omitted=true로 보내세요): "
								+ (original.getStepIndex() + 1) + "번째 단계");
			}
		}

		List<StepAdjustment> adjustments = new ArrayList<>();
		for (RecipeStepEntity original : originals) {
			ExecutedRecipe.ExecutedStep actual = executedByOriginal.get(original.getId());
			if (actual.omitted()) {
				adjustments.add(new StepAdjustment(
						original.getId(), AdjustmentType.REMOVE,
						null, original.getStepIndex(), null, null, null));
				continue;
			}
			validateStep(actual);
			String changedInstruction = sameText(original.getInstruction(), actual.instruction())
					? null : actual.instruction().trim();
			Integer changedTimer = java.util.Objects.equals(
					original.getTimerSeconds(), actual.timerSeconds()) ? null : actual.timerSeconds();
			String changedCaution = sameText(original.getCautionNote(), actual.cautionNote())
					? null : trimToNull(actual.cautionNote());
			if (original.getTimerSeconds() != null && actual.timerSeconds() == null) {
				throw new IllegalArgumentException("MVP에서는 기존 타이머 제거를 지원하지 않습니다.");
			}
			if (original.getCautionNote() != null && trimToNull(actual.cautionNote()) == null) {
				throw new IllegalArgumentException("MVP에서는 기존 주의 문구 제거를 지원하지 않습니다.");
			}
			if (changedInstruction != null || changedTimer != null || changedCaution != null) {
				adjustments.add(new StepAdjustment(
						original.getId(), AdjustmentType.MODIFY,
						null, actual.sortOrder(),
						changedInstruction, changedTimer, changedCaution));
			}
		}

		int lastOriginalStepIndex = -1;
		int addedOrder = 0;
		for (ExecutedRecipe.ExecutedStep actual : ordered) {
			if (actual.originalStepId() != null) {
				lastOriginalStepIndex = originalsById.get(actual.originalStepId()).getStepIndex();
				continue;
			}
			if (actual.omitted()) {
				continue;
			}
			validateStep(actual);
			adjustments.add(new StepAdjustment(
					null, AdjustmentType.ADD,
					lastOriginalStepIndex, addedOrder++,
					actual.instruction().trim(),
					actual.timerSeconds(),
					trimToNull(actual.cautionNote())));
		}
		return List.copyOf(adjustments);
	}

	private void validateIngredient(ExecutedRecipe.ExecutedIngredient ingredient) {
		if (ingredient.name() == null || ingredient.name().isBlank()) {
			throw new IllegalArgumentException("실행 재료의 name은 필수입니다.");
		}
		if (ingredient.amount() != null && ingredient.amount().signum() < 0) {
			throw new IllegalArgumentException("실행 재료의 amount는 0 이상이어야 합니다.");
		}
	}

	private void validateStep(ExecutedRecipe.ExecutedStep step) {
		if (step.instruction() == null || step.instruction().isBlank()) {
			throw new IllegalArgumentException("실행 단계의 instruction은 필수입니다.");
		}
		if (step.timerSeconds() != null && step.timerSeconds() < 0) {
			throw new IllegalArgumentException("실행 단계의 timerSeconds는 0 이상이어야 합니다.");
		}
	}

	private BigDecimal normalizeAmount(
			BigDecimal actual, BigDecimal baseServings, BigDecimal targetServings) {
		if (actual == null) {
			return null;
		}
		return actual.multiply(baseServings)
				.divide(targetServings, 2, RoundingMode.HALF_UP)
				.stripTrailingZeros();
	}

	private boolean sameAmount(BigDecimal left, BigDecimal right) {
		return left == null ? right == null : right != null && left.compareTo(right) == 0;
	}

	private boolean sameText(String left, String right) {
		return java.util.Objects.equals(trimToNull(left), trimToNull(right));
	}

	private boolean sameIngredientAdjustments(
			List<IngredientAdjustment> left, List<IngredientAdjustment> right) {
		if (left.size() != right.size()) {
			return false;
		}
		boolean[] matched = new boolean[right.size()];
		for (IngredientAdjustment candidate : left) {
			boolean found = false;
			for (int index = 0; index < right.size(); index++) {
				if (!matched[index] && sameIngredientAdjustment(candidate, right.get(index))) {
					matched[index] = true;
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private boolean sameIngredientAdjustment(IngredientAdjustment left, IngredientAdjustment right) {
		return java.util.Objects.equals(left.originalIngredientId(), right.originalIngredientId())
				&& left.type() == right.type()
				&& sameText(left.name(), right.name())
				&& sameAmount(left.amount(), right.amount())
				&& sameText(left.unit(), right.unit())
				&& java.util.Objects.equals(left.required(), right.required())
				&& left.sortOrder() == right.sortOrder();
	}

	private boolean sameStepAdjustments(
			List<StepAdjustment> left, List<StepAdjustment> right) {
		if (left.size() != right.size()) {
			return false;
		}
		boolean[] matched = new boolean[right.size()];
		for (StepAdjustment candidate : left) {
			boolean found = false;
			for (int index = 0; index < right.size(); index++) {
				if (!matched[index] && sameStepAdjustment(candidate, right.get(index))) {
					matched[index] = true;
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private boolean sameStepAdjustment(StepAdjustment left, StepAdjustment right) {
		return java.util.Objects.equals(left.originalStepId(), right.originalStepId())
				&& left.type() == right.type()
				&& java.util.Objects.equals(left.insertAfterStepIndex(), right.insertAfterStepIndex())
				&& left.sortOrder() == right.sortOrder()
				&& sameText(left.instruction(), right.instruction())
				&& java.util.Objects.equals(left.timerSeconds(), right.timerSeconds())
				&& sameText(left.cautionNote(), right.cautionNote());
	}

	private String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String buildSummary(
			List<IngredientAdjustment> ingredientAdjustments,
			List<StepAdjustment> stepAdjustments) {
		List<String> parts = new ArrayList<>();
		for (IngredientAdjustment adjustment : ingredientAdjustments) {
			String target = adjustment.name() != null
					? adjustment.name()
					: "재료";
			parts.add(switch (adjustment.type()) {
				case ADD -> target + " 추가";
				case MODIFY -> target + " 조정";
				case REMOVE -> "재료 생략";
			});
		}
		for (StepAdjustment adjustment : stepAdjustments) {
			parts.add(switch (adjustment.type()) {
				case ADD -> "조리 단계 추가";
				case MODIFY -> "조리 단계 조정";
				case REMOVE -> "조리 단계 생략";
			});
		}
		return String.join(" · ", parts.stream().limit(5).toList());
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
