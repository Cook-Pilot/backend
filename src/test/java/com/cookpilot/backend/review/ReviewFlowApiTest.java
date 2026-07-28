package com.cookpilot.backend.review;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;
import com.cookpilot.backend.personalrecipe.AdjustmentType;
import com.cookpilot.backend.personalrecipe.DeriveVersionRequest;
import com.cookpilot.backend.personalrecipe.IngredientAdjustment;
import com.cookpilot.backend.personalrecipe.PersonalRecipeService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersion;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionRepository;
import com.cookpilot.backend.personalrecipe.StepAdjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewFlowApiTest extends PostgresApiTestBase {

	private static final String ING_RICE = "20000000-0000-0000-0000-000000000201";
	private static final String ING_KIMCHI = "20000000-0000-0000-0000-000000000202";
	private static final String ING_OIL = "20000000-0000-0000-0000-000000000203";
	private static final String ING_EGG = "20000000-0000-0000-0000-000000000204";
	private static final String STEP_HEAT = "30000000-0000-0000-0000-000000000201";
	private static final String STEP_KIMCHI = "30000000-0000-0000-0000-000000000202";
	private static final String STEP_RICE = "30000000-0000-0000-0000-000000000203";
	private static final String STEP_SERVE = "30000000-0000-0000-0000-000000000204";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PostCookReviewRepository reviewRepository;

	@Autowired
	private PersonalRecipeVersionRepository personalRecipeVersionRepository;

	@Autowired
	private PersonalRecipeService personalRecipeService;

	@Test
	void 실행_변경이_있을_때만_개인_버전을_생성한다() throws Exception {
		JsonNode unchanged = submitReview(UUID.randomUUID(), 1, 1, 100, 1, 1);
		assertThat(unchanged.get("createdPersonalVersionId").isNull()).isTrue();

		JsonNode changed = submitReview(UUID.randomUUID(), 1, 1, 80, 1, 1);
		String reviewId = changed.get("id").asText();
		String versionId = changed.get("createdPersonalVersionId").asText();

		mockMvc.perform(get("/api/v1/personal-versions/" + versionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version.sourceReviewId").value(reviewId))
				.andExpect(jsonPath("$.ingredients", hasSize(4)))
				.andExpect(jsonPath("$.ingredientAdjustments", hasSize(1)))
				.andExpect(jsonPath("$.ingredientAdjustments[0].type").value("MODIFY"))
				.andExpect(jsonPath("$.ingredientAdjustments[0].amount").value(80));
	}

	@Test
	void 인분에_맞춘_단순_비례_증가는_개인_변경이_아니다() throws Exception {
		JsonNode review = submitReview(UUID.randomUUID(), 2, 2, 200, 2, 2);

		assertThat(review.get("targetServings").decimalValue()).isEqualByComparingTo("2");
		assertThat(review.get("createdPersonalVersionId").isNull()).isTrue();
	}

	@Test
	void 동일한_조리_세션을_재전송하면_한_번만_저장한다() throws Exception {
		UUID clientSessionId = UUID.randomUUID();
		JsonNode first = submitReview(clientSessionId, 1, 1, 70, 1, 1);
		JsonNode retried = submitReview(clientSessionId, 1, 1, 70, 1, 1);

		assertThat(retried.get("id").asText()).isEqualTo(first.get("id").asText());
		assertThat(retried.get("createdPersonalVersionId").asText())
				.isEqualTo(first.get("createdPersonalVersionId").asText());
	}

	@Test
	void 동일한_조리_세션을_동시에_전송해도_한_번만_저장한다() throws Exception {
		UUID clientSessionId = UUID.randomUUID();
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<JsonNode> firstRequest = executor.submit(() -> {
				start.await();
				return submitReview(clientSessionId, 1, 1, 70, 1, 1);
			});
			Future<JsonNode> secondRequest = executor.submit(() -> {
				start.await();
				return submitReview(clientSessionId, 1, 1, 70, 1, 1);
			});

			start.countDown();
			JsonNode first = firstRequest.get(10, TimeUnit.SECONDS);
			JsonNode second = secondRequest.get(10, TimeUnit.SECONDS);

			assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());
			assertThat(second.get("createdPersonalVersionId").asText())
					.isEqualTo(first.get("createdPersonalVersionId").asText());

			PostCookReviewEntity saved = reviewRepository
					.findByUserIdAndClientSessionId(
							UUID.fromString("00000000-0000-0000-0000-000000000001"),
							clientSessionId)
					.orElseThrow();
			assertThat(personalRecipeVersionRepository.findBySourceReviewIdIn(
					List.of(saved.getId()))).hasSize(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 선택한_개인_버전을_그대로_실행하면_중복_버전을_만들지_않는다() throws Exception {
		JsonNode first = submitReview(UUID.randomUUID(), 1, 1, 75, 1, 1);
		String sourceVersionId = first.get("createdPersonalVersionId").asText();

		JsonNode repeated = submitReview(
				UUID.randomUUID(), 1, 1, 75, 1, 1, sourceVersionId);

		assertThat(repeated.get("sourcePersonalVersionId").asText()).isEqualTo(sourceVersionId);
		assertThat(repeated.get("createdPersonalVersionId").isNull()).isTrue();
	}

	@Test
	void REMOVE와_제거된_단계_뒤_ADD가_있는_개인_버전도_그대로_실행하면_중복_버전을_만들지_않는다()
			throws Exception {
		PersonalRecipeVersion source = createVersionWithRemoveAndAnchoredAdd();

		String response = postSourceReview(
				source,
				sourceIngredientSnapshot(1) + ",\n"
						+ sourceStepSnapshot("김치 대신 파를 30초 볶으세요.", 30))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assertThat(objectMapper.readTree(response).get("createdPersonalVersionId").isNull())
				.isTrue();
	}

	@Test
	void source에서_REMOVE되지_않은_원본을_누락하면_계속_400이다() throws Exception {
		PersonalRecipeVersion source = createVersionWithRemoveAndAnchoredAdd();

		postSourceReview(source, """
				"ingredients": [
				  {"originalIngredientId":"%s","name":"밥","amount":1,"unit":"공기","required":true,"sortOrder":0},
				  {"originalIngredientId":"%s","name":"식용유","amount":1,"unit":"큰술","required":true,"sortOrder":1},
				  {"name":"참기름","amount":1,"unit":"작은술","required":false,"sortOrder":2}
				]
				""".formatted(ING_RICE, ING_OIL))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(
						"실행 스냅샷에 원본 재료가 누락되었습니다(사용하지 않았다면 omitted=true로 보내세요): 김치"));
	}

	@Test
	void source에서_REMOVE되지_않은_원본_단계를_누락하면_계속_400이다() throws Exception {
		PersonalRecipeVersion source = createVersionWithRemoveAndAnchoredAdd();

		postSourceReview(source, """
				"steps": [
				  {"originalStepId":"%s","instruction":"팬에 기름을 두르고 중불로 달구세요.","timerSeconds":60,"cautionNote":"기름이 튈 수 있어요","sortOrder":0},
				  {"instruction":"김치 대신 파를 30초 볶으세요.","timerSeconds":30,"sortOrder":1},
				  {"originalStepId":"%s","instruction":"불을 끄고 그릇에 담으세요.","sortOrder":2}
				]
				""".formatted(STEP_HEAT, STEP_SERVE))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(
						"실행 스냅샷에 원본 단계가 누락되었습니다(수행하지 않았다면 omitted=true로 보내세요): 3번째 단계"));
	}

	@Test
	void source_ADD를_다른_위치의_단계로_바꾸면_클라이언트가_보낸_새_위치를_사용한다()
			throws Exception {
		PersonalRecipeVersion source = createVersionWithRemoveAndAnchoredAdd();

		String response = postSourceReview(source, """
				"steps": [
				  {"originalStepId":"%s","instruction":"팬에 기름을 두르고 중불로 달구세요.","timerSeconds":60,"cautionNote":"기름이 튈 수 있어요","sortOrder":0},
				  {"originalStepId":"%s","instruction":"밥을 넣고 3분간 볶으세요.","timerSeconds":180,"sortOrder":1},
				  {"originalStepId":"%s","instruction":"불을 끄고 그릇에 담으세요.","sortOrder":2},
				  {"instruction":"마지막에 참기름을 둘러 마무리하세요.","sortOrder":3}
				]
				""".formatted(STEP_HEAT, STEP_RICE, STEP_SERVE))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		UUID createdVersionId = UUID.fromString(objectMapper.readTree(response)
				.get("createdPersonalVersionId").asText());
		var sourceDetail = personalRecipeService.findDetailById(source.id());
		var createdDetail = personalRecipeService.findDetailById(createdVersionId);
		List<StepAdjustment> addedSteps = createdDetail
				.stepAdjustments()
				.stream()
				.filter(adjustment -> adjustment.type() == AdjustmentType.ADD)
				.toList();

		assertThat(createdDetail.ingredientAdjustments())
				.containsExactlyElementsOf(sourceDetail.ingredientAdjustments());
		assertThat(addedSteps)
				.singleElement()
				.extracting(StepAdjustment::insertAfterStepIndex)
				.isEqualTo(3);
	}

	@Test
	void 재료_스냅샷만_보내도_source의_단계_REMOVE와_ADD를_보존한다()
			throws Exception {
		PersonalRecipeVersion source = createVersionWithRemoveAndAnchoredAdd();

		String response = postSourceReview(source, sourceIngredientSnapshot(2))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		UUID createdVersionId = UUID.fromString(objectMapper.readTree(response)
				.get("createdPersonalVersionId").asText());
		var sourceDetail = personalRecipeService.findDetailById(source.id());
		var createdDetail = personalRecipeService.findDetailById(createdVersionId);

		assertThat(createdDetail.stepAdjustments())
				.containsExactlyElementsOf(sourceDetail.stepAdjustments());
	}

	@Test
	void 제거된_단계_뒤_source_ADD의_내용만_바꾸면_기존_anchor를_유지한다()
			throws Exception {
		PersonalRecipeVersion source = createVersionWithRemoveAndAnchoredAdd();

		String response = postSourceReview(
				source,
				sourceStepSnapshot("김치 대신 파를 90초 충분히 볶으세요.", 90))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		UUID createdVersionId = UUID.fromString(objectMapper.readTree(response)
				.get("createdPersonalVersionId").asText());
		List<StepAdjustment> addedSteps = personalRecipeService.findDetailById(createdVersionId)
				.stepAdjustments()
				.stream()
				.filter(adjustment -> adjustment.type() == AdjustmentType.ADD)
				.toList();

		assertThat(addedSteps).hasSize(1);
		assertThat(addedSteps.getFirst().insertAfterStepIndex()).isEqualTo(1);
		assertThat(addedSteps.getFirst().instruction())
				.isEqualTo("김치 대신 파를 90초 충분히 볶으세요.");
		assertThat(addedSteps.getFirst().timerSeconds()).isEqualTo(90);
	}

	@Test
	void 여러_source_ADD의_순서를_바꿔도_위치별_anchor와_sortOrder를_보존한다()
			throws Exception {
		PersonalRecipeVersion source = createVersionWithMultipleAdds();

		String response = postSourceReview(source, """
				"ingredients": [
				  {"originalIngredientId":"%s","name":"밥","amount":1,"unit":"공기","required":true,"sortOrder":0},
				  {"originalIngredientId":"%s","name":"김치","amount":100,"unit":"g","required":true,"sortOrder":1},
				  {"originalIngredientId":"%s","name":"식용유","amount":1,"unit":"큰술","required":true,"sortOrder":2},
				  {"name":"깨","amount":1,"unit":"큰술","required":false,"sortOrder":3},
				  {"name":"참기름","amount":1,"unit":"작은술","required":false,"sortOrder":4}
				],
				"steps": [
				  {"originalStepId":"%s","instruction":"팬에 기름을 두르고 중불로 달구세요.","timerSeconds":60,"cautionNote":"기름이 튈 수 있어요","sortOrder":0},
				  {"instruction":"대파를 10초 더 볶으세요.","timerSeconds":10,"sortOrder":1},
				  {"instruction":"김치 대신 파를 30초 볶으세요.","timerSeconds":30,"sortOrder":2},
				  {"originalStepId":"%s","instruction":"밥을 넣고 3분간 볶으세요.","timerSeconds":180,"sortOrder":3},
				  {"originalStepId":"%s","instruction":"불을 끄고 그릇에 담으세요.","sortOrder":4}
				]
				""".formatted(
				ING_RICE, ING_KIMCHI, ING_OIL,
				STEP_HEAT, STEP_RICE, STEP_SERVE))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		UUID createdVersionId = UUID.fromString(objectMapper.readTree(response)
				.get("createdPersonalVersionId").asText());
		var detail = personalRecipeService.findDetailById(createdVersionId);
		List<IngredientAdjustment> addedIngredients = detail.ingredientAdjustments()
				.stream()
				.filter(adjustment -> adjustment.type() == AdjustmentType.ADD)
				.toList();
		List<StepAdjustment> addedSteps = detail.stepAdjustments()
				.stream()
				.filter(adjustment -> adjustment.type() == AdjustmentType.ADD)
				.toList();

		assertThat(addedIngredients)
				.extracting(IngredientAdjustment::name)
				.containsExactly("깨", "참기름");
		assertThat(addedIngredients)
				.extracting(IngredientAdjustment::sortOrder)
				.containsExactly(7, 8);
		assertThat(addedSteps)
				.extracting(StepAdjustment::instruction)
				.containsExactly("대파를 10초 더 볶으세요.", "김치 대신 파를 30초 볶으세요.");
		assertThat(addedSteps)
				.extracting(StepAdjustment::insertAfterStepIndex)
				.containsExactly(1, 1);
		assertThat(addedSteps)
				.extracting(StepAdjustment::sortOrder)
				.containsExactly(7, 8);
	}

	@Test
	void 레시피별_리뷰와_기간별_조리_이력을_조회한다() throws Exception {
		JsonNode review = submitReview(UUID.randomUUID(), 1, 1, 90, 1, 1);

		mockMvc.perform(get("/api/v1/recipes/" + TestRecipeIds.FRIED_RICE_RECIPE_ID + "/reviews"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

		mockMvc.perform(get("/api/v1/cooking-history")
						.param("from", "2026-01-01T00:00:00Z")
						.param("to", "2027-01-01T00:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
				.andExpect(jsonPath("$[?(@.reviewId == '" + review.get("id").asText()
						+ "')].recipeTitle").value("김치볶음밥"));
	}

	@Test
	void 개인_버전_목록은_최근_5개까지만_반환한다() throws Exception {
		for (int i = 0; i < 6; i++) {
			submitReview(UUID.randomUUID(), 1, 1, 60 + i, 1, 1);
		}

		mockMvc.perform(get("/api/v1/recipes/" + TestRecipeIds.FRIED_RICE_RECIPE_ID
						+ "/personal-versions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)));
	}

	@Test
	void 잘못된_입력과_없는_리소스는_각각_400과_404() throws Exception {
		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "%s",
								  "rating": 6
								}
								""".formatted(UUID.randomUUID(), TestRecipeIds.FRIED_RICE_RECIPE_ID)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "99999999-0000-0000-0000-000000000000",
								  "rating": 3
								}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/reviews/99999999-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 기존_재료의_양이나_단위를_비울_수_없다() throws Exception {
		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "%s",
								  "targetServings": 1,
								  "rating": 4,
								  "ingredients": [
								    {"originalIngredientId":"%s","name":"밥","amount":null,"unit":"공기","required":true,"omitted":false,"sortOrder":0},
								    {"originalIngredientId":"%s","name":"김치","amount":100,"unit":"g","required":true,"omitted":false,"sortOrder":1},
								    {"originalIngredientId":"%s","name":"식용유","amount":1,"unit":"큰술","required":true,"omitted":false,"sortOrder":2},
								    {"originalIngredientId":"%s","name":"계란","amount":1,"unit":"개","required":false,"omitted":false,"sortOrder":3}
								  ]
								}
								""".formatted(
								UUID.randomUUID(),
								TestRecipeIds.FRIED_RICE_RECIPE_ID,
								ING_RICE, ING_KIMCHI, ING_OIL, ING_EGG)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail")
						.value("MVP에서는 기존 재료의 양 제거를 지원하지 않습니다."));

		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "%s",
								  "targetServings": 1,
								  "rating": 4,
								  "ingredients": [
								    {"originalIngredientId":"%s","name":"밥","amount":1,"unit":"","required":true,"omitted":false,"sortOrder":0},
								    {"originalIngredientId":"%s","name":"김치","amount":100,"unit":"g","required":true,"omitted":false,"sortOrder":1},
								    {"originalIngredientId":"%s","name":"식용유","amount":1,"unit":"큰술","required":true,"omitted":false,"sortOrder":2},
								    {"originalIngredientId":"%s","name":"계란","amount":1,"unit":"개","required":false,"omitted":false,"sortOrder":3}
								  ]
								}
								""".formatted(
								UUID.randomUUID(),
								TestRecipeIds.FRIED_RICE_RECIPE_ID,
								ING_RICE, ING_KIMCHI, ING_OIL, ING_EGG)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail")
						.value("MVP에서는 기존 재료의 단위 제거를 지원하지 않습니다."));
	}

	@Test
	void 실행_스냅샷이_원본_재료를_누락하면_400() throws Exception {
		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "%s",
								  "targetServings": 1,
								  "rating": 4,
								  "ingredients": [
								    {"originalIngredientId":"%s","name":"밥","amount":2,"unit":"공기","required":true,"omitted":false,"sortOrder":0}
								  ]
								}
								""".formatted(
								UUID.randomUUID(),
								TestRecipeIds.FRIED_RICE_RECIPE_ID,
								ING_RICE)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(
						"실행 스냅샷에 원본 재료가 누락되었습니다(사용하지 않았다면 omitted=true로 보내세요): 김치"));
	}

	@Test
	void 스냅샷_없이_개인_버전만_선택하면_후기만_저장한다() throws Exception {
		JsonNode first = submitReview(UUID.randomUUID(), 1, 1, 85, 1, 1);
		String sourceVersionId = first.get("createdPersonalVersionId").asText();

		String response = mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "%s",
								  "targetServings": 1,
								  "sourcePersonalVersionId": "%s",
								  "rating": 5,
								  "comment": "별점만 남긴다"
								}
								""".formatted(
								UUID.randomUUID(),
								TestRecipeIds.FRIED_RICE_RECIPE_ID,
								sourceVersionId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assertThat(objectMapper.readTree(response).get("createdPersonalVersionId").isNull())
				.isTrue();
	}

	@Test
	void 단계를_omitted로_생략하면_REMOVE_버전이_생성된다() throws Exception {
		String response = mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "%s",
								  "targetServings": 1,
								  "rating": 4,
								  "steps": [
								    {"originalStepId":"%s","instruction":"팬에 기름을 두르고 중불로 달구세요.","timerSeconds":60,"cautionNote":"기름이 튈 수 있어요","sortOrder":0},
								    {"originalStepId":"%s","instruction":"김치를 넣고 2분간 볶으세요.","timerSeconds":120,"sortOrder":1},
								    {"originalStepId":"%s","instruction":"밥을 넣고 3분간 볶으세요.","timerSeconds":180,"sortOrder":2},
								    {"originalStepId":"%s","omitted":true,"sortOrder":3}
								  ]
								}
								""".formatted(
								UUID.randomUUID(),
								TestRecipeIds.FRIED_RICE_RECIPE_ID,
								STEP_HEAT, STEP_KIMCHI, STEP_RICE, STEP_SERVE)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String versionId = objectMapper.readTree(response)
				.get("createdPersonalVersionId").asText();

		mockMvc.perform(get("/api/v1/personal-versions/" + versionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stepAdjustments", hasSize(1)))
				.andExpect(jsonPath("$.stepAdjustments[0].type").value("REMOVE"));
	}

	private ResultActions postSourceReview(
			PersonalRecipeVersion source, String snapshotFields) throws Exception {
		return mockMvc.perform(post("/api/v1/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "clientSessionId": "%s",
						  "recipeId": "%s",
						  "targetServings": 1,
						  "sourcePersonalVersionId": "%s",
						  "rating": 4,
						  %s
						}
						""".formatted(
						UUID.randomUUID(),
						TestRecipeIds.FRIED_RICE_RECIPE_ID,
						source.id(),
						snapshotFields)));
	}

	private String sourceIngredientSnapshot(int riceAmount) {
		return """
				"ingredients": [
				  {"originalIngredientId":"%s","name":"밥","amount":%d,"unit":"공기","required":true,"sortOrder":0},
				  {"originalIngredientId":"%s","name":"김치","amount":100,"unit":"g","required":true,"sortOrder":1},
				  {"originalIngredientId":"%s","name":"식용유","amount":1,"unit":"큰술","required":true,"sortOrder":2},
				  {"name":"참기름","amount":1,"unit":"작은술","required":false,"sortOrder":3}
				]
				""".formatted(ING_RICE, riceAmount, ING_KIMCHI, ING_OIL);
	}

	private String sourceStepSnapshot(String addedInstruction, int addedTimerSeconds) {
		return """
				"steps": [
				  {"originalStepId":"%s","instruction":"팬에 기름을 두르고 중불로 달구세요.","timerSeconds":60,"cautionNote":"기름이 튈 수 있어요","sortOrder":0},
				  {"instruction":"%s","timerSeconds":%d,"sortOrder":1},
				  {"originalStepId":"%s","instruction":"밥을 넣고 3분간 볶으세요.","timerSeconds":180,"sortOrder":2},
				  {"originalStepId":"%s","instruction":"불을 끄고 그릇에 담으세요.","sortOrder":3}
				]
				""".formatted(
				STEP_HEAT, addedInstruction, addedTimerSeconds, STEP_RICE, STEP_SERVE);
	}

	private PersonalRecipeVersion createVersionWithRemoveAndAnchoredAdd() throws Exception {
		JsonNode parent = submitReview(UUID.randomUUID(), 1, 1, 80, 1, 1);
		return personalRecipeService.derive(
				UUID.fromString(parent.get("createdPersonalVersionId").asText()),
				new DeriveVersionRequest(
						"김치 없이 볶는 버전",
						"계란과 김치 볶기 단계를 빼고 참기름과 대체 단계를 추가",
						List.of(
								new IngredientAdjustment(
										UUID.fromString(ING_EGG),
										AdjustmentType.REMOVE,
										null, null, null, null, 41),
								new IngredientAdjustment(
										null,
										AdjustmentType.ADD,
										"참기름",
										BigDecimal.ONE,
										"작은술",
										false,
										7)),
						List.of(
								new StepAdjustment(
										UUID.fromString(STEP_KIMCHI),
										AdjustmentType.REMOVE,
										null, 41, null, null, null),
								new StepAdjustment(
										null,
										AdjustmentType.ADD,
										1, 7,
										"김치 대신 파를 30초 볶으세요.",
										30,
										null))));
	}

	private PersonalRecipeVersion createVersionWithMultipleAdds() throws Exception {
		PersonalRecipeVersion source = createVersionWithRemoveAndAnchoredAdd();
		var sourceDetail = personalRecipeService.findDetailById(source.id());
		List<IngredientAdjustment> ingredientAdjustments = new java.util.ArrayList<>(
				sourceDetail.ingredientAdjustments());
		ingredientAdjustments.add(new IngredientAdjustment(
				null,
				AdjustmentType.ADD,
				"깨",
				BigDecimal.ONE,
				"큰술",
				false,
				8));
		List<StepAdjustment> stepAdjustments = new java.util.ArrayList<>(
				sourceDetail.stepAdjustments());
		stepAdjustments.add(new StepAdjustment(
				null,
				AdjustmentType.ADD,
				1, 8,
				"대파를 10초 더 볶으세요.",
				10,
				null));
		return personalRecipeService.derive(
				source.id(),
				new DeriveVersionRequest(
						"ADD가 여러 개인 버전",
						"ADD 순서 보존 테스트",
						List.copyOf(ingredientAdjustments),
						List.copyOf(stepAdjustments)));
	}

	private JsonNode submitReview(
			UUID clientSessionId,
			int targetServings,
			int riceAmount,
			int kimchiAmount,
			int oilAmount,
			int eggAmount) throws Exception {
		return submitReview(clientSessionId, targetServings, riceAmount, kimchiAmount,
				oilAmount, eggAmount, null);
	}

	private JsonNode submitReview(
			UUID clientSessionId,
			int targetServings,
			int riceAmount,
			int kimchiAmount,
			int oilAmount,
			int eggAmount,
			String sourcePersonalVersionId) throws Exception {
		String body = """
				{
				  "clientSessionId": "%s",
				  "recipeId": "%s",
				  "cookedAt": "2026-07-26T01:00:00Z",
				  "targetServings": %d,
				  "sourcePersonalVersionId": %s,
				  "rating": 4,
				  "comment": "맛있었다",
				  "nextTimeNote": "다음에도 이대로",
				  "ingredients": [
				    {"originalIngredientId":"%s","name":"밥","amount":%d,"unit":"공기","required":true,"omitted":false,"sortOrder":0},
				    {"originalIngredientId":"%s","name":"김치","amount":%d,"unit":"g","required":true,"omitted":false,"sortOrder":1},
				    {"originalIngredientId":"%s","name":"식용유","amount":%d,"unit":"큰술","required":true,"omitted":false,"sortOrder":2},
				    {"originalIngredientId":"%s","name":"계란","amount":%d,"unit":"개","required":false,"omitted":false,"sortOrder":3}
				  ],
				  "steps": [
				    {"originalStepId":"%s","instruction":"팬에 기름을 두르고 중불로 달구세요.","timerSeconds":60,"cautionNote":"기름이 튈 수 있어요","sortOrder":0},
				    {"originalStepId":"%s","instruction":"김치를 넣고 2분간 볶으세요.","timerSeconds":120,"sortOrder":1},
				    {"originalStepId":"%s","instruction":"밥을 넣고 3분간 볶으세요.","timerSeconds":180,"sortOrder":2},
				    {"originalStepId":"%s","instruction":"불을 끄고 그릇에 담으세요.","sortOrder":3}
				  ]
				}
				""".formatted(
				clientSessionId,
				TestRecipeIds.FRIED_RICE_RECIPE_ID,
				targetServings,
				sourcePersonalVersionId == null ? "null" : "\"" + sourcePersonalVersionId + "\"",
				ING_RICE, riceAmount,
				ING_KIMCHI, kimchiAmount,
				ING_OIL, oilAmount,
				ING_EGG, eggAmount,
				STEP_HEAT, STEP_KIMCHI, STEP_RICE, STEP_SERVE);
		String response = mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response);
	}
}
