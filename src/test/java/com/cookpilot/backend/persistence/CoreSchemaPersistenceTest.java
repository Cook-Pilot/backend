package com.cookpilot.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.cookpilot.backend.personalrecipe.AdjustmentType;
import com.cookpilot.backend.personalrecipe.PersonalIngredientAdjustmentEntity;
import com.cookpilot.backend.personalrecipe.PersonalIngredientAdjustmentRepository;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionEntity;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionRepository;
import com.cookpilot.backend.personalrecipe.PersonalStepAdjustmentEntity;
import com.cookpilot.backend.personalrecipe.PersonalStepAdjustmentRepository;
import com.cookpilot.backend.recipe.RecipeEntity;
import com.cookpilot.backend.recipe.RecipeIngredientEntity;
import com.cookpilot.backend.recipe.RecipeIngredientRepository;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.recipe.RecipeStepEntity;
import com.cookpilot.backend.recipe.RecipeStepRepository;
import com.cookpilot.backend.review.PostCookReviewEntity;
import com.cookpilot.backend.review.PostCookReviewRepository;
import com.cookpilot.backend.user.UserEntity;
import com.cookpilot.backend.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolationException;

/**
 * 그룹 A 코어 스키마의 적용 테스트.
 *
 * 운영과 동일한 postgres:16-alpine 컨테이너에서:
 *  - 컨텍스트가 뜬다는 것 자체가 "Flyway V1 마이그레이션 적용 + JPA ddl-auto=validate 통과"를 증명한다.
 *    (엔티티 매핑과 실제 스키마가 어긋나면 여기서 컨텍스트 로딩이 실패한다.)
 *  - 각 리포지토리의 저장/조회 왕복과 JSONB 라운드트립을 검증한다.
 *
 * 실행 요건: 로컬/CI에 Docker 데몬 필요(Testcontainers).
 */
@SpringBootTest
@ActiveProfiles("db")
@Testcontainers
@Transactional
class CoreSchemaPersistenceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@PersistenceContext
	private EntityManager em;

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RecipeRepository recipeRepository;
	@Autowired
	private RecipeIngredientRepository ingredientRepository;
	@Autowired
	private RecipeStepRepository stepRepository;
	@Autowired
	private PersonalRecipeVersionRepository versionRepository;
	@Autowired
	private PersonalIngredientAdjustmentRepository ingredientAdjustmentRepository;
	@Autowired
	private PersonalStepAdjustmentRepository stepAdjustmentRepository;
	@Autowired
	private PostCookReviewRepository reviewRepository;

	@Test
	void 컨텍스트가_뜨면_스키마_검증이_통과한_것이다() {
		// Flyway 적용 + JPA validate 통과 시에만 이 지점에 도달한다.
		assertThat(userRepository).isNotNull();
	}

	@Test
	void 레시피와_재료_단계를_저장하고_정렬조회한다() {
		RecipeEntity recipe = recipeRepository.save(
				new RecipeEntity("라면", "기본 라면", new BigDecimal("1.00")));

		ingredientRepository.save(new RecipeIngredientEntity(recipe.getId(), "물", new BigDecimal("500.00"), "ml", true, 0));
		ingredientRepository.save(new RecipeIngredientEntity(recipe.getId(), "면", null, null, true, 1));
		stepRepository.save(new RecipeStepEntity(recipe.getId(), 0, "물 500ml를 넣고 끓인다.", 180, null));
		stepRepository.save(new RecipeStepEntity(recipe.getId(), 1, "면과 스프를 넣는다.", 180, "화상 주의"));
		flushAndClear();

		List<RecipeIngredientEntity> ingredients = ingredientRepository.findByRecipeIdOrderBySortOrderAsc(recipe.getId());
		List<RecipeStepEntity> steps = stepRepository.findByRecipeIdOrderByStepIndexAsc(recipe.getId());

		assertThat(ingredients).extracting(RecipeIngredientEntity::getName).containsExactly("물", "면");
		assertThat(steps).extracting(RecipeStepEntity::getStepIndex).containsExactly(0, 1);
		assertThat(steps.get(0).getCreatedAt()).isNotNull(); // timestamptz 매핑 확인
	}

	@Test
	void 레시피와_단계의_이미지URL을_왕복한다() {
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity(
				"부대찌개", null, null, "https://cdn.example.com/recipes/budae.jpg"));
		stepRepository.save(new RecipeStepEntity(
				recipe.getId(), 0, "재료를 냄비에 담는다.", null, null, "https://cdn.example.com/steps/budae-0.jpg"));
		flushAndClear();

		RecipeEntity found = recipeRepository.findById(recipe.getId()).orElseThrow();
		List<RecipeStepEntity> steps = stepRepository.findByRecipeIdOrderByStepIndexAsc(recipe.getId());

		assertThat(found.getImageUrl()).isEqualTo("https://cdn.example.com/recipes/budae.jpg");
		assertThat(steps.get(0).getImageUrl()).isEqualTo("https://cdn.example.com/steps/budae-0.jpg");
	}

	@Test
	void 이미지URL이_없으면_null로_저장된다() {
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity("계란찜", null, null));
		stepRepository.save(new RecipeStepEntity(recipe.getId(), 0, "계란을 푼다.", null, null));
		flushAndClear();

		assertThat(recipeRepository.findById(recipe.getId()).orElseThrow().getImageUrl()).isNull();
		assertThat(stepRepository.findByRecipeIdOrderByStepIndexAsc(recipe.getId()).get(0).getImageUrl()).isNull();
	}

	@Test
	void 개인레시피버전과_리뷰를_저장한다() {
		UserEntity user = userRepository.save(new UserEntity("v@test.com", "버전유저"));
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity("된장국", null, null));

		PostCookReviewEntity review = reviewRepository.save(new PostCookReviewEntity(
				user.getId(), recipe.getId(), 5, "맛있었다", "다음엔 파 추가"));

		// 조리 1회의 기록은 리뷰다. 버전은 자기를 만든 리뷰를 source_review_id 로 가리킨다.
		PersonalRecipeVersionEntity v1 = new PersonalRecipeVersionEntity(
				user.getId(), recipe.getId(), 1, "덜 짜게", "소금 감소", review.getId());
		v1.setDefault(true);
		v1 = versionRepository.save(v1);
		UUID v1Id = v1.getId();

		// v1에서 진화한 v2 (원본이 아니라 v1을 부모로 가진다).
		PersonalRecipeVersionEntity v2 = new PersonalRecipeVersionEntity(
				user.getId(), recipe.getId(), 2, "더 덜 짜게", "소금 더 감소", null);
		v2.setParentVersionId(v1Id);
		versionRepository.save(v2);

		flushAndClear();

		assertThat(versionRepository.findByUserIdAndRecipeIdAndIsDefaultTrue(user.getId(), recipe.getId()))
				.isPresent()
				.get()
				.satisfies(v -> assertThat(v.getVersionNumber()).isEqualTo(1));

		// 진화 계보: v1의 자식으로 v2가 조회된다.
		List<PersonalRecipeVersionEntity> children = versionRepository.findByParentVersionId(v1Id);
		assertThat(children).hasSize(1);
		assertThat(children.get(0).getVersionNumber()).isEqualTo(2);

		List<PostCookReviewEntity> reviews = reviewRepository.findByRecipeIdOrderByCreatedAtDesc(recipe.getId());
		assertThat(reviews).hasSize(1);
		assertThat(reviews.get(0).getRating()).isEqualTo(5);

		assertThat(versionRepository.findById(v1Id).orElseThrow().getSourceReviewId())
				.isEqualTo(review.getId());
	}

	@Test
	void 이메일로_사용자를_조회한다() {
		UUID id = userRepository.save(new UserEntity("find@test.com", "찾기")).getId();
		flushAndClear();

		assertThat(userRepository.findByEmail("find@test.com"))
				.isPresent()
				.get()
				.satisfies(u -> assertThat(u.getId()).isEqualTo(id));
	}

	@Test
	void 별점이_범위를_벗어나면_flush_전에_검증에서_막힌다() {
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity("검증", null, null));

		// rating=6 은 @Max(5) 위반 → Hibernate 가 flush 시점 검증에서 ConstraintViolationException.
		// (bean validation 예외라 Spring 예외 변환 대상이 아니라 그대로 전파된다.)
		assertThatThrownBy(() -> reviewRepository.saveAndFlush(new PostCookReviewEntity(
				null, recipe.getId(), 6, "범위 초과", null)))
				.isInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void 조리이력이_있는_레시피는_삭제가_RESTRICT로_차단된다() {
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity("삭제금지", null, null));
		reviewRepository.save(new PostCookReviewEntity(null, recipe.getId(), 5, "조리 이력", null));
		flushAndClear();

		// post_cook_reviews.recipe_id 는 ON DELETE RESTRICT → 리뷰가 남아 있으면 레시피 삭제 불가.
		// 리포지토리 프록시의 flush() 를 타야 Spring 이 DataIntegrityViolationException 으로 변환한다.
		recipeRepository.deleteById(recipe.getId());
		assertThatThrownBy(() -> recipeRepository.flush())
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 존재하지_않는_리뷰를_참조하는_버전은_FK로_거부된다() {
		UserEntity user = userRepository.save(new UserEntity("fk@test.com", "FK"));
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity("FK검증", null, null));

		// source_review_id 가 실제 리뷰가 아니면 FK 제약 위반.
		assertThatThrownBy(() -> versionRepository.saveAndFlush(new PersonalRecipeVersionEntity(
				user.getId(), recipe.getId(), 1, "버전", null, UUID.randomUUID())))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 리뷰의_structured_feedback_JSONB를_왕복한다() {
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity("JSONB", null, null));
		PostCookReviewEntity review = new PostCookReviewEntity(null, recipe.getId(), 4, "코멘트", null);
		review.setStructuredFeedback(Map.of("salt", "-20%"));
		UUID reviewId = reviewRepository.save(review).getId();
		flushAndClear();

		assertThat(reviewRepository.findById(reviewId).orElseThrow().getStructuredFeedback())
				.containsEntry("salt", "-20%");
	}

	@Test
	void 재료와_단계_diff를_저장하고_버전_삭제시_함께_지워진다() {
		UserEntity user = userRepository.save(new UserEntity("diff@test.com", "diff"));
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity("diff레시피", null, null));
		RecipeIngredientEntity water = ingredientRepository.save(
				new RecipeIngredientEntity(recipe.getId(), "물", new BigDecimal("500.00"), "ml", true, 0));
		RecipeStepEntity boil = stepRepository.save(
				new RecipeStepEntity(recipe.getId(), 0, "끓인다", 180, null));

		PersonalRecipeVersionEntity version = versionRepository.save(new PersonalRecipeVersionEntity(
				user.getId(), recipe.getId(), 1, "diff버전", null, null));

		ingredientAdjustmentRepository.save(new PersonalIngredientAdjustmentEntity(
				version.getId(), water.getId(), AdjustmentType.MODIFY, null, new BigDecimal("400.00"),
				null, null, 0));
		ingredientAdjustmentRepository.save(new PersonalIngredientAdjustmentEntity(
				version.getId(), null, AdjustmentType.ADD, "소금", new BigDecimal("1.00"), "꼬집",
				false, 1));
		stepAdjustmentRepository.save(new PersonalStepAdjustmentEntity(
				version.getId(), boil.getId(), AdjustmentType.MODIFY, null, 0, "약불로 끓인다", null, null));
		stepAdjustmentRepository.save(new PersonalStepAdjustmentEntity(
				version.getId(), null, AdjustmentType.ADD, 0, 0, "소금을 넣는다", null, null));
		flushAndClear();

		assertThat(ingredientAdjustmentRepository.findByPersonalVersionIdOrderBySortOrderAsc(version.getId()))
				.hasSize(2)
				.extracting(PersonalIngredientAdjustmentEntity::getAdjustmentType)
				.containsExactly(AdjustmentType.MODIFY, AdjustmentType.ADD);
		assertThat(stepAdjustmentRepository.findByPersonalVersionIdOrderBySortOrderAsc(version.getId()))
				.hasSize(2);

		// ON DELETE CASCADE: 버전이 지워지면 diff 행도 함께 사라진다.
		versionRepository.deleteById(version.getId());
		versionRepository.flush();
		em.clear();
		assertThat(ingredientAdjustmentRepository.findByPersonalVersionIdOrderBySortOrderAsc(version.getId()))
				.isEmpty();
		assertThat(stepAdjustmentRepository.findByPersonalVersionIdOrderBySortOrderAsc(version.getId()))
				.isEmpty();
	}

	@Test
	void ADD_diff가_원본을_참조하면_CHECK로_거부된다() {
		UserEntity user = userRepository.save(new UserEntity("check@test.com", "check"));
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity("check레시피", null, null));
		RecipeIngredientEntity water = ingredientRepository.save(
				new RecipeIngredientEntity(recipe.getId(), "물", null, null, true, 0));
		PersonalRecipeVersionEntity version = versionRepository.save(new PersonalRecipeVersionEntity(
				user.getId(), recipe.getId(), 1, "check버전", null, null));

		// ADD 는 원본 참조가 있으면 안 된다(DB CHECK).
		assertThatThrownBy(() -> ingredientAdjustmentRepository.saveAndFlush(
				new PersonalIngredientAdjustmentEntity(version.getId(), water.getId(),
						AdjustmentType.ADD, "소금", null, null, null, 0)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void MODIFY_diff가_원본_참조없이_들어오면_CHECK로_거부된다() {
		UserEntity user = userRepository.save(new UserEntity("check2@test.com", "check2"));
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity("check2레시피", null, null));
		PersonalRecipeVersionEntity version = versionRepository.save(new PersonalRecipeVersionEntity(
				user.getId(), recipe.getId(), 1, "check2버전", null, null));

		assertThatThrownBy(() -> ingredientAdjustmentRepository.saveAndFlush(
				new PersonalIngredientAdjustmentEntity(version.getId(), null,
						AdjustmentType.MODIFY, null, new BigDecimal("1.00"), null, null, 0)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	/** DB까지 강제 반영 후 영속성 컨텍스트를 비워, 다음 조회가 실제 SELECT를 타게 한다(진짜 왕복 검증). */
	private void flushAndClear() {
		em.flush();
		em.clear();
	}
}
