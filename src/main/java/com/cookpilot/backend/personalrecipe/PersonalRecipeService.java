package com.cookpilot.backend.personalrecipe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.recipe.IngredientEntity;
import com.cookpilot.backend.recipe.IngredientRepository;
import com.cookpilot.backend.recipe.RecipeEntity;
import com.cookpilot.backend.recipe.RecipeIngredientRepository;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.recipe.RecipeStepRepository;
import com.cookpilot.backend.review.PostCookReviewEntity;
import com.cookpilot.backend.review.PostCookReviewRepository;
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
	private final IngredientRepository ingredientRepository;
	private final PostCookReviewRepository reviewRepository;
	private final UserService userService;

	public PersonalRecipeService(PersonalRecipeVersionRepository versionRepository,
			PersonalIngredientAdjustmentRepository ingredientAdjustmentRepository,
			PersonalStepAdjustmentRepository stepAdjustmentRepository,
			RecipeRepository recipeRepository,
			RecipeIngredientRepository recipeIngredientRepository,
			RecipeStepRepository recipeStepRepository,
			IngredientRepository ingredientRepository,
			PostCookReviewRepository reviewRepository,
			UserService userService) {
		this.versionRepository = versionRepository;
		this.ingredientAdjustmentRepository = ingredientAdjustmentRepository;
		this.stepAdjustmentRepository = stepAdjustmentRepository;
		this.recipeRepository = recipeRepository;
		this.recipeIngredientRepository = recipeIngredientRepository;
		this.recipeStepRepository = recipeStepRepository;
		this.ingredientRepository = ingredientRepository;
		this.reviewRepository = reviewRepository;
		this.userService = userService;
	}

	/**
	 * 한 번의 조리에서 나온 수정 사항(setup/cooking/review 층)으로 원본 기준 누적 diff 버전을
	 * 만든다. 결과가 원본과 같으면 Optional.empty() — 버전을 만들지 않는다.
	 *
	 * 입력은 층별로 이미 타입(ADD/MODIFY/REMOVE)이 붙은 diff 다. 서버는 검증만 하고
	 * 스냅샷 역산은 하지 않는다(명시적 diff 계약). 프론트가 원본 기준으로 머지해서 보내므로
	 * 부모 버전의 diff 는 읽지 않는다 — 부모는 계보(parent_version_id)로만 남는다.
	 *
	 * 층은 (원본, 앞 층 diff) → diff 로 이어진다. 원본은 절대 변하지 않으므로 한 번만 읽고,
	 * 층을 통과하며 바뀌는 것은 diff 뿐이다. 마지막에 남은 diff 를 저장한다.
	 *
	 * 멱등: 리뷰 하나가 만드는 버전은 최대 하나다(uq_personal_versions_source_review).
	 * 멱등 게이트는 소유자 확인 뒤에 둔다 — 앞에 두면 이미 버전이 있는 남의 reviewId 로
	 * 404 대신 그 사람의 버전이 나간다.
	 * 리뷰가 clientSessionId 로 멱등해서 재시도해도 reviewId 가 같으므로, 이 게이트가 순차
	 * 재전송을 막는다. 동시 재전송은 lockCurrentUser 의 사용자 행 락이 직렬화한다 —
	 * 그게 없으면 두 요청이 같은 version_number 를 읽고 UNIQUE 위반으로 500 이 된다.
	 *
	 * 조리 1회의 사실은 리뷰 행에서 읽는다 — 레시피, 인분, 부모 버전, 그리고 review 층의 입력.
	 * 요청 본문으로도 받으면 같은 사실의 쓰기 가능한 사본이 둘이 되고, 어긋나도 에러가 나지 않는다.
	 * 조회가 소유자 스코프(findByIdAndUserId)라 남의 리뷰를 지목하면 404 이고, 레시피를 리뷰에서
	 * 뽑으므로 "리뷰와 다른 레시피" 케이스는 표현 자체가 불가능하다.
	 *
	 * + 추가로 맛 벡터 등이 추가되면 여기에서 참조할 수 있게 AI 입력을 넣어주면 될 듯 합니다~
	 */
	@Transactional
	public Optional<PersonalRecipeVersion> createFromEdits(UUID reviewId, RecipeEditRequest request) {
		UUID userId = userService.lockCurrentUser().id();

		PostCookReviewEntity review = reviewRepository.findByIdAndUserId(reviewId, userId)
				.orElseThrow(() -> new NotFoundException("조리 기록을 찾을 수 없습니다: " + reviewId));

		Optional<PersonalRecipeVersionEntity> alreadyCreated =
				versionRepository.findFirstBySourceReviewId(reviewId);
		if (alreadyCreated.isPresent()) {
			return alreadyCreated.map(PersonalRecipeVersion::from);
		}

		UUID recipeId = review.getRecipeId();
		RecipeEntity recipe = recipeRepository.findById(recipeId)
				.orElseThrow(() -> new NotFoundException("레시피를 찾을 수 없습니다: " + recipeId));

		UUID parentVersionId = review.getSourcePersonalVersionId();
		validateSourceVersion(userId, recipeId, parentVersionId);

		Originals originals = readOriginals(recipeId);
		Diff diff = applySetup(
				originals, request.setup(), review.getTargetServings(), recipe.getBaseServings());
		diff = applyCooking(originals, diff, request.cooking());
		diff = applyReview(originals, diff, review);
		if (diff.isEmpty()) {
			return Optional.empty();
		}

		int nextVersionNumber = nextVersionNumber(userId, recipeId);
		PersonalRecipeVersionEntity entity = new PersonalRecipeVersionEntity(
				userId, recipeId, nextVersionNumber,
				recipe.getTitle() + " - 내 버전 v" + nextVersionNumber,
				buildSummary(diff), reviewId);
		entity.setParentVersionId(parentVersionId);
		PersonalRecipeVersionEntity saved = versionRepository.save(entity);

		ingredientAdjustmentRepository.saveAll(diff.ingredients().stream()
				.map(adj -> new PersonalIngredientAdjustmentEntity(saved.getId(),
						adj.originalIngredientId(), adj.type(), resolveIngredient(adj.name()),
						adj.amount(), adj.unit(), adj.required(), adj.sortOrder(),
						adj.amountSpecified()))
				.toList());
		stepAdjustmentRepository.saveAll(diff.steps().stream()
				.map(adj -> new PersonalStepAdjustmentEntity(saved.getId(), adj.originalStepId(),
						adj.type(), adj.insertAfterStepIndex(), adj.sortOrder(), adj.instruction(),
						adj.timerSeconds(), adj.cautionNote()))
				.toList());
		return Optional.of(PersonalRecipeVersion.from(saved));
	}

	/**
	 * setup 층: UI 편집에서 온 정형 diff. LLM 을 거치지 않는다.
	 *
	 * amount 는 조리 인분 기준으로 오므로 1인분 기준으로 되돌린 뒤, 되돌린 결과가 원본과
	 * 같아진 MODIFY 를 버린다 — 인분만 바꾼 조리가 여기서 빈 diff 가 된다.
	 *
	 * 검증은 되돌리기 전에 한다 — normalize 는 요청 본문을 그대로 훑으므로 검증이 뒤면
	 * 깨진 입력(리스트 안의 null 등)이 검증 전에 NPE 로 터져 400 대신 500 이 된다.
	 * 되돌리기는 부호와 null 여부를 바꾸지 않아 검증 결과도 달라지지 않는다.
	 *
	 * 재료를 건드리면 리뷰에 targetServings 가 있어야 한다. 없으면 조리 인분 기준 양이 1인분
	 * 기준으로 그대로 저장돼 조용히 배수만큼 어긋난다 — 에러 없이 틀리느니 400 이 낫다.
	 * 단계만 고치는 수정(타이머 등)은 양과 무관하므로 요구하지 않는다.
	 */
	private Diff applySetup(Originals originals, RecipeEditRequest.Setup setup,
			BigDecimal targetServings, BigDecimal baseServings) {
		if (setup == null) {
			return Diff.EMPTY;
		}
		List<IngredientAdjustment> ingredients = setup.ingredientAdjustmentsOrEmpty();
		if (!ingredients.isEmpty() && (targetServings == null || targetServings.signum() <= 0)) {
			throw new IllegalArgumentException(
					"재료를 수정하려면 조리 기록에 targetServings(0보다 큰 값)가 있어야 합니다.");
		}
		Diff diff = new Diff(ingredients, setup.stepAdjustmentsOrEmpty());
		validate(originals, diff);
		return dropNoOpModifies(originals, new Diff(
				normalizeAmounts(diff.ingredients(), baseServings, targetServings), diff.steps()));
	}

	/**
	 * cooking 층: 조리 중 발화를 앞 층 diff 에 반영해 갱신된 원본 기준 완결 diff 를 낸다.
	 * 자연어가 없으면 층을 통째로 건너뛴다 — 침묵 = 앞 층 결과 그대로 통과.
	 *
	 * TODO(AI 확정 후) LLM 호출을 여기 넣는다. 넣을 때 지켜야 하는 것:
	 *  - 프롬프트의 "현재 상태"는 diff 가 아니라 DiffComposer 로 합성한 결과여야 한다
	 *    (모델은 MODIFY(amount=1.5) 를 못 읽고 "고추장 1.5스푼" 은 읽는다)
	 *  - 모델 출력의 원본 참조는 UUID 가 아니라 원본 행 번호로 받고 서버가 매핑한다
	 *  - 결과는 validate/dropNoOpModifies 를 다시 통과시킨다 (LLM 은 신뢰 경계 밖)
	 * 배관이 없는 동안은 앞 층 결과를 그대로 통과시킨다.
	 */
	private Diff applyCooking(Originals originals, Diff base, RecipeEditRequest.Cooking cooking) {
		if (cooking == null || cooking.isBlank()) {
			return base;
		}
		// TODO(AI 확정 후) 여기가 LLM 호출 자리다. 배관이 없으므로 발화가 있어도 통과시킨다.
		return base;
	}

	/**
	 * review 층: 조리 후 리뷰를 앞 층 diff 에 반영한다. 마지막 층이므로 이 결과가 저장된다.
	 * cooking 층과 계약이 같다 — 침묵이면 통과, 결과는 원본 기준 완결 diff.
	 *
	 * 입력은 요청이 아니라 리뷰 행이다(comment / next_time_note). 같은 문장을 본문으로도
	 * 받으면 리뷰에 적힌 것과 다른 것을 보낼 수 있고, 무엇이 정본인지 정할 근거가 없다.
	 *
	 * TODO(AI 확정 후) applyCooking 과 같은 조건으로 LLM 호출을 넣는다. 추가로,
	 * 조리 후 해석은 피드백 루프가 없어(틀려도 다음 조리까지 모른다) 사용자 확인 UI 가 필수다.
	 * cooking transcript 도 컨텍스트로 같이 줘야 앞 층 결정을 되돌릴 수 있다.
	 */
	private Diff applyReview(Originals originals, Diff base, PostCookReviewEntity review) {
		if (isBlank(review.getComment()) && isBlank(review.getNextTimeNote())) {
			return base;
		}
		// TODO(AI 확정 후) 여기가 LLM 호출 자리다. 배관이 없으므로 리뷰 본문이 있어도 통과시킨다.
		return base;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	/** 원본은 불변이라 층마다 다시 읽지 않는다. 층 함수는 JPA 엔티티를 보지 않는다. */
	private Originals readOriginals(UUID recipeId) {
		Map<UUID, DiffComposer.OriginalIngredient> ingredients = new HashMap<>();
		recipeIngredientRepository.findByRecipeIdOrderBySortOrderAsc(recipeId)
				.forEach(i -> ingredients.put(i.getId(), new DiffComposer.OriginalIngredient(
						i.getId(), i.getName(), i.getAmount(), i.getUnit(), i.isRequired(),
						i.getSortOrder())));
		Map<UUID, DiffComposer.OriginalStep> steps = new HashMap<>();
		recipeStepRepository.findByRecipeIdOrderByStepIndexAsc(recipeId)
				.forEach(s -> steps.put(s.getId(), new DiffComposer.OriginalStep(
						s.getId(), s.getStepIndex(), s.getInstruction(), s.getTimerSeconds(),
						s.getCautionNote())));
		return new Originals(ingredients, steps);
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

		Originals originals = readOriginals(entity.getRecipeId());

		return new PersonalRecipeVersionDetail(
				PersonalRecipeVersion.from(entity),
				DiffComposer.composeIngredients(
						List.copyOf(originals.ingredients().values()), ingredientAdjustments),
				DiffComposer.composeSteps(
						List.copyOf(originals.steps().values()), stepAdjustments),
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

	/**
	 * diff 의 재료 이름을 ingredients 마스터 행으로 바꾼다(없으면 생성). 이름 정규화(trim,
	 * 동의어 통합)는 후속 이슈. NULL 은 "재료 지정 없음"(MODIFY 유지/REMOVE)이라 그대로 둔다.
	 */
	private IngredientEntity resolveIngredient(String name) {
		if (name == null) {
			return null;
		}
		return ingredientRepository.findByName(name)
				.orElseGet(() -> ingredientRepository.save(new IngredientEntity(name)));
	}

	private int nextVersionNumber(UUID userId, UUID recipeId) {
		return versionRepository.findByUserIdAndRecipeIdOrderByVersionNumberDesc(userId, recipeId)
				.stream().findFirst()
				.map(v -> v.getVersionNumber() + 1)
				.orElse(1);
	}

	/**
	 * 조리에 쓴 개인 버전이 이 레시피 소속인지. 다른 레시피의 버전을 지목하면 400.
	 * 그대로 parent_version_id 가 되므로 계보가 꼬이면 안 된다.
	 */
	/**
	 * 조리에 쓴 개인 버전이 이 사용자·이 레시피의 것인지. 리뷰 저장(ReviewService)과 버전
	 * 생성 양쪽에서 부른다 — 리뷰의 source_personal_version_id 가 곧 새 버전의 계보 부모라
	 * 리뷰가 저장되는 시점에 이미 대조돼 있어야 한다.
	 */
	public void validateSourceVersion(UUID userId, UUID recipeId, UUID sourceVersionId) {
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

	/**
	 * 조리 인분 기준으로 온 amount 를 1인분 기준으로 되돌린다. 인분은 diff 에 들어가지 않는다.
	 * 인분 정보가 없거나 base 와 같으면 손대지 않는다. REMOVE 는 amount 가 없어 그대로 통과한다.
	 */
	private List<IngredientAdjustment> normalizeAmounts(List<IngredientAdjustment> adjustments,
			BigDecimal baseServings, BigDecimal targetServings) {
		if (targetServings == null || targetServings.signum() <= 0
				|| targetServings.compareTo(baseServings) == 0) {
			return adjustments;
		}
		return adjustments.stream()
				.map(adj -> new IngredientAdjustment(adj.originalIngredientId(), adj.type(),
						adj.name(), normalizeAmount(adj.amount(), baseServings, targetServings),
						adj.amountSpecified(), adj.unit(), adj.required(), adj.sortOrder()))
				.toList();
	}

	/** targetServings 조리량 → 1인분(baseServings) 기준으로 되돌린다. createFromEdits 의 setup 층용. */
	private BigDecimal normalizeAmount(
			BigDecimal actual, BigDecimal baseServings, BigDecimal targetServings) {
		if (actual == null) {
			return null;
		}
		return actual.multiply(baseServings)
				.divide(targetServings, 2, RoundingMode.HALF_UP)
				.stripTrailingZeros();
	}

	/**
	 * 원본과 다를 게 없는 MODIFY 를 버린다. ADD/REMOVE 는 그 자체가 변경이라 항상 남는다.
	 *
	 * validate 를 통과한 뒤에만 부른다 — MODIFY 의 원본 참조가 이 레시피 것임이 보장돼야
	 * originals.get 이 null 이 아니다.
	 */
	private Diff dropNoOpModifies(Originals originals, Diff diff) {
		return new Diff(
				diff.ingredients().stream()
						.filter(adj -> adj.type() != AdjustmentType.MODIFY
								|| overridesAnything(adj,
										originals.ingredients().get(adj.originalIngredientId())))
						.toList(),
				diff.steps().stream()
						.filter(adj -> adj.type() != AdjustmentType.MODIFY
								|| overridesAnything(adj,
										originals.steps().get(adj.originalStepId())))
						.toList());
	}

	/** MODIFY 의 지정된 필드 중 원본과 실제로 다른 것이 하나라도 있는지. */
	private boolean overridesAnything(
			IngredientAdjustment adj, DiffComposer.OriginalIngredient original) {
		return differs(adj.name(), original.name())
				|| differsAmount(adj, original.amount())
				|| differs(adj.unit(), original.unit())
				|| (adj.required() != null && adj.required() != original.required());
	}

	/** amount 는 키 생략(유지)과 명시적 null(제거)을 별도로 비교한다. */
	private boolean differsAmount(IngredientAdjustment adj, BigDecimal originalAmount) {
		if (!adj.amountSpecified()) {
			return false;
		}
		if (adj.amount() == null) {
			return originalAmount != null;
		}
		return originalAmount == null || originalAmount.compareTo(adj.amount()) != 0;
	}

	private boolean overridesAnything(StepAdjustment adj, DiffComposer.OriginalStep original) {
		return differs(adj.instruction(), original.instruction())
				|| differs(adj.timerSeconds(), original.timerSeconds())
				|| differs(adj.cautionNote(), original.cautionNote());
	}

	/** override 가 null 이면 "원본 값 유지"이므로 차이로 세지 않는다. */
	private boolean differs(Object override, Object originalValue) {
		return override != null && !override.equals(originalValue);
	}

	/** diff 에서 사람이 읽는 요약을 만든다. createFromEdits 의 저장 단계용. */
	private String buildSummary(Diff diff) {
		List<String> parts = new ArrayList<>();
		for (IngredientAdjustment adjustment : diff.ingredients()) {
			String target = adjustment.name() != null
					? adjustment.name()
					: "재료";
			parts.add(switch (adjustment.type()) {
				case ADD -> target + " 추가";
				case MODIFY -> target + " 조정";
				case REMOVE -> "재료 생략";
			});
		}
		for (StepAdjustment adjustment : diff.steps()) {
			parts.add(switch (adjustment.type()) {
				case ADD -> "조리 단계 추가";
				case MODIFY -> "조리 단계 조정";
				case REMOVE -> "조리 단계 생략";
			});
		}
		return String.join(" · ", parts.stream().limit(5).toList());
	}

	/**
	 * 층 출력이 저장 가능한 diff 인지. 층마다 출력 타입이 같으므로 층마다 이걸 통과시킨다.
	 *
	 * DB CHECK 와 겹치는 검사가 있지만 여기서 잡아야 400 이 된다 — DB 가 잡으면
	 * DataIntegrityViolationException 이라 500 이다. DB 가 아예 모르는 검사도 있다 —
	 * "원본 참조가 이 레시피 것인지"(FK 는 존재만 본다), 값의 범위(음수 amount/timer,
	 * 공백 문자열), 한 원본 행에 조정이 둘 이상인지(UNIQUE 가 없고, 있어도 합성 결과가
	 * 어느 쪽인지 정해지지 않는다). 이것들은 여기서만 잡힌다.
	 */
	private void validate(Originals originals, Diff diff) {
		validateIngredientAdjustments(originals.ingredients(), diff.ingredients());
		validateStepAdjustments(originals.steps(), diff.steps());
	}

	private void validateIngredientAdjustments(
			Map<UUID, DiffComposer.OriginalIngredient> originals,
			List<IngredientAdjustment> adjustments) {
		Set<UUID> referenced = new HashSet<>();
		for (IngredientAdjustment adj : adjustments) {
			if (adj == null) {
				throw new IllegalArgumentException("재료 조정에 빈 항목이 있습니다.");
			}
			if (adj.type() == null) {
				throw new IllegalArgumentException("재료 조정에 type은 필수입니다.");
			}
			if (adj.name() != null && adj.name().isBlank()) {
				throw new IllegalArgumentException("재료 조정의 name은 공백일 수 없습니다.");
			}
			if (adj.amount() != null && adj.amount().signum() < 0) {
				throw new IllegalArgumentException("재료 조정의 amount는 0 이상이어야 합니다.");
			}
			if (adj.type() == AdjustmentType.ADD) {
				if (adj.originalIngredientId() != null) {
					throw new IllegalArgumentException("ADD 재료 조정은 원본 재료를 참조할 수 없습니다.");
				}
				if (adj.name() == null) {
					throw new IllegalArgumentException("ADD 재료 조정에 name은 필수입니다.");
				}
			} else {
				if (adj.originalIngredientId() == null) {
					throw new IllegalArgumentException(adj.type() + " 재료 조정에 원본 재료 참조는 필수입니다.");
				}
				if (!originals.containsKey(adj.originalIngredientId())) {
					throw new IllegalArgumentException(
							"이 레시피의 재료가 아닙니다: " + adj.originalIngredientId());
				}
				if (!referenced.add(adj.originalIngredientId())) {
					throw new IllegalArgumentException(
							"같은 원본 재료를 두 번 조정할 수 없습니다: " + adj.originalIngredientId());
				}
			}
		}
	}

	private void validateStepAdjustments(
			Map<UUID, DiffComposer.OriginalStep> originals, List<StepAdjustment> adjustments) {
		Set<UUID> referenced = new HashSet<>();
		for (StepAdjustment adj : adjustments) {
			if (adj == null) {
				throw new IllegalArgumentException("단계 조정에 빈 항목이 있습니다.");
			}
			if (adj.type() == null) {
				throw new IllegalArgumentException("단계 조정에 type은 필수입니다.");
			}
			if (adj.instruction() != null && adj.instruction().isBlank()) {
				throw new IllegalArgumentException("단계 조정의 instruction은 공백일 수 없습니다.");
			}
			if (adj.timerSeconds() != null && adj.timerSeconds() < 0) {
				throw new IllegalArgumentException("단계 조정의 timerSeconds는 0 이상이어야 합니다.");
			}
			if (adj.type() == AdjustmentType.ADD) {
				if (adj.originalStepId() != null) {
					throw new IllegalArgumentException("ADD 단계 조정은 원본 단계를 참조할 수 없습니다.");
				}
				if (adj.instruction() == null) {
					throw new IllegalArgumentException("ADD 단계 조정에 instruction은 필수입니다.");
				}
				if (adj.insertAfterStepIndex() == null || adj.insertAfterStepIndex() < -1) {
					throw new IllegalArgumentException("ADD 단계 조정에 insertAfterStepIndex(-1 이상)는 필수입니다.");
				}
			} else {
				if (adj.originalStepId() == null) {
					throw new IllegalArgumentException(adj.type() + " 단계 조정에 원본 단계 참조는 필수입니다.");
				}
				if (!originals.containsKey(adj.originalStepId())) {
					throw new IllegalArgumentException("이 레시피의 단계가 아닙니다: " + adj.originalStepId());
				}
				if (!referenced.add(adj.originalStepId())) {
					throw new IllegalArgumentException(
							"같은 원본 단계를 두 번 조정할 수 없습니다: " + adj.originalStepId());
				}
				// 앵커는 ADD 전용이다. 비ADD 는 원본 위치를 그대로 쓰므로 DB CHECK 도 NULL 을 요구한다.
				if (adj.insertAfterStepIndex() != null) {
					throw new IllegalArgumentException(
							adj.type() + " 단계 조정은 insertAfterStepIndex를 가질 수 없습니다.");
				}
			}
		}
	}
}
