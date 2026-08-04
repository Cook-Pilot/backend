package com.cookpilot.backend.personalrecipe;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;
import com.cookpilot.backend.user.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수정 파이프라인 엔드포인트(POST /reviews/{reviewId}/personal-versions).
 *
 * 경로가 조리 1회의 기록(리뷰)을 지목하고 본문은 층 입력만 갖는다. 레시피·인분·부모 버전·
 * 리뷰 본문은 리뷰 행에서 읽으므로 본문으로 보낼 수 없다 — 그래서 이 테스트들은 조건을
 * 요청이 아니라 먼저 저장하는 리뷰로 만든다.
 *
 * 지금 살아있는 층은 setup 하나 — cooking/review 는 LLM 배관이 없어 앞 층 결과를 그대로
 * 통과시킨다. 검증하는 계약:
 *  - 정형 diff 를 그대로 저장하고 조회 시 원본 + diff 로 합성한다
 *  - amount 는 조리 인분 기준으로 오고 서버가 리뷰의 인분으로 1인분 기준까지 되돌린다
 *  - 되돌린 결과가 원본과 같으면 버전을 만들지 않는다 (204)
 *  - 같은 reviewId 로 재전송하면 처음 만든 버전을 그대로 돌려준다
 *  - 원본 참조는 이 레시피 것이어야 한다 (FK 로는 못 잡는 검증)
 *  - DB 제약에 걸릴 입력은 500 이 아니라 4xx 로 돌아온다
 *
 * V2 시드 라면 레시피를 쓴다(base_servings = 1).
 *
 * 클래스에 @Transactional 을 붙이지 않는다. 붙이면 서비스 트랜잭션이 테스트 트랜잭션에
 * 합류해 커밋이 없고, flush 가 후속 조회에 딸려갈 때만 일어난다 — FK·CHECK 위반이
 * 조용히 통과해서 아래 4xx 테스트가 의미를 잃는다. 대신 버전 번호가 다른 테스트 클래스와
 * 누적되므로 절대값 대신 계보와 상대값만 단언한다.
 */
class RecipeEditPipelineApiTest extends PostgresApiTestBase {

	// V2__personal_diff_and_seed.sql 의 라면 시드 고정 UUID
	private static final UUID RAMEN_ID = TestRecipeIds.RAMEN_RECIPE_ID;
	private static final UUID ING_WATER = UUID.fromString("20000000-0000-0000-0000-000000000102");
	private static final UUID ING_EGG = UUID.fromString("20000000-0000-0000-0000-000000000103");
	private static final UUID STEP_BOIL = UUID.fromString("30000000-0000-0000-0000-000000000101");
	// 김치볶음밥 재료 — 라면 diff 가 참조하면 안 되는 남의 레시피 행
	private static final UUID FRIED_RICE_KIMCHI =
			UUID.fromString("20000000-0000-0000-0000-000000000202");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void setup_diff를_저장하고_조회하면_원본과_합성해서_돌려준다() throws Exception {
		UUID reviewId = submitReview(RAMEN_ID, 1);
		JsonNode version = createVersion(reviewId, """
				{
				  "setup": {
				    "ingredientAdjustments": [
				      {"originalIngredientId": "%s", "type": "MODIFY",
				       "amount": 400, "sortOrder": 0},
				      {"originalIngredientId": "%s", "type": "REMOVE", "sortOrder": 1}
				    ],
				    "stepAdjustments": []
				  }
				}
				""".formatted(ING_WATER, ING_EGG));

		// 버전 번호는 (user, recipe) 스코프 카운터라 다른 테스트 클래스가 남긴 버전에 영향받는다.
		// 절대값 대신 계보와 리뷰 역참조를 단언한다.
		assertThat(version.get("parentVersionId").isNull()).isTrue();
		assertThat(version.get("sourceReviewId").asString()).isEqualTo(reviewId.toString());

		// 레시피를 본문으로 받지 않으므로 리뷰가 지목한 레시피의 버전이 된다.
		assertThat(version.get("recipeId").asString()).isEqualTo(RAMEN_ID.toString());

		JsonNode detail = getDetail(version.get("id").asString());

		// 원시 diff 는 보낸 그대로 남는다 — 서버는 역산하지 않는다.
		assertThat(detail.get("ingredientAdjustments")).hasSize(2);

		// 합성 결과: 물은 400 으로 덮이고, 계란은 빠지고, 나머지는 원본 그대로.
		JsonNode ingredients = detail.get("ingredients");
		assertThat(namesOf(ingredients)).containsExactly("라면", "물", "파");
		assertThat(originAt(ingredients, "물")).isEqualTo("MODIFIED");
		assertThat(amountAt(ingredients, "물")).isEqualByComparingTo("400");
		assertThat(originAt(ingredients, "라면")).isEqualTo("ORIGINAL");
	}

	@Test
	void 같은_reviewId로_두_번_보내면_처음_만든_버전을_그대로_돌려준다() throws Exception {
		UUID reviewId = submitReview(RAMEN_ID, 1);
		JsonNode first = createVersion(reviewId, editBody("400"));

		// 두 번째 본문이 달라도 새 버전을 만들지 않는다 — 리뷰당 버전은 하나다.
		JsonNode second = createVersion(reviewId, editBody("300"));

		assertThat(second.get("id").asString()).isEqualTo(first.get("id").asString());
		assertThat(second.get("versionNumber").asInt())
				.isEqualTo(first.get("versionNumber").asInt());
	}

	@Test
	void 리뷰_응답이_생성된_버전을_역참조한다() throws Exception {
		UUID reviewId = submitReview(RAMEN_ID, 1);
		JsonNode version = createVersion(reviewId, editBody("400"));

		String response = mockMvc.perform(get("/api/v1/reviews/" + reviewId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(objectMapper.readTree(response).get("createdPersonalVersionId").asString())
				.isEqualTo(version.get("id").asString());
	}

	@Test
	void 인분만_바꾼_조리는_버전을_만들지_않는다() throws Exception {
		// 4인분으로 끓이며 물 2000ml — 1인분 기준으로 되돌리면 500ml 로 원본과 같다.
		expectNoVersion(submitReview(RAMEN_ID, 4), editBody("2000"));
	}

	@Test
	void 인분을_바꾸면서_비율까지_바꾸면_되돌린_양이_diff로_남는다() throws Exception {
		// 4인분에 물 2400ml → 1인분 기준 600ml. 나누는 인분은 리뷰에서 온다.
		JsonNode version = createVersion(submitReview(RAMEN_ID, 4), editBody("2400"));

		JsonNode detail = getDetail(version.get("id").asString());
		assertThat(amountAt(detail.get("ingredients"), "물")).isEqualByComparingTo("600");
	}

	@Test
	void MODIFY_amount_명시적_null은_양을_제거해_저장하고_조회한다() throws Exception {
		// targetServings != baseServings 여도 null presence 가 정규화 과정에서 사라지면 안 된다.
		JsonNode version = createVersion(submitReview(RAMEN_ID, 4), """
				{
				  "setup": {
				    "ingredientAdjustments": [
				      {"originalIngredientId": "%s", "type": "MODIFY",
				       "amount": null, "sortOrder": 0}
				    ],
				    "stepAdjustments": []
				  }
				}
				""".formatted(ING_WATER));

		JsonNode detail = getDetail(version.get("id").asString());
		JsonNode water = findByName(detail.get("ingredients"), "물");
		assertThat(water.get("amount").isNull()).isTrue();

		JsonNode rawAdjustment = detail.get("ingredientAdjustments").get(0);
		assertThat(rawAdjustment.get("amount").isNull()).isTrue();
		assertThat(rawAdjustment.get("amountSpecified").asBoolean()).isTrue();
	}

	@Test
	void MODIFY_amount_키_생략은_다른_필드만_바꾸고_원본_양을_유지한다() throws Exception {
		JsonNode version = createVersion(submitReview(RAMEN_ID, 4), """
				{
				  "setup": {
				    "ingredientAdjustments": [
				      {"originalIngredientId": "%s", "type": "MODIFY",
				       "name": "정수", "sortOrder": 0}
				    ],
				    "stepAdjustments": []
				  }
				}
				""".formatted(ING_WATER));

		JsonNode detail = getDetail(version.get("id").asString());
		assertThat(amountAt(detail.get("ingredients"), "정수")).isEqualByComparingTo("500");
		assertThat(detail.get("ingredientAdjustments").get(0)
				.get("amountSpecified").asBoolean()).isFalse();
	}

	@Test
	void 원본과_같은_값의_MODIFY는_버전을_만들지_않는다() throws Exception {
		// 타이머를 원본과 같은 180 초로 보냈다 — 바꾼 것이 없다.
		expectNoVersion(submitReview(RAMEN_ID, 1), """
				{
				  "setup": {
				    "ingredientAdjustments": [],
				    "stepAdjustments": [
				      {"originalStepId": "%s", "type": "MODIFY",
				       "insertAfterStepIndex": null, "sortOrder": 0, "timerSeconds": 180}
				    ]
				  }
				}
				""".formatted(STEP_BOIL));
	}

	@Test
	void 자연어만_있고_정형_수정이_없으면_버전을_만들지_않는다() throws Exception {
		// review 층 입력은 리뷰 행의 comment 다. cooking/review 둘 다 LLM 배관이 없어 통과만 한다.
		UUID reviewId = submitReview(RAMEN_ID, 1, null, "조금 싱거웠다");
		expectNoVersion(reviewId, """
				{
				  "setup": null,
				  "cooking": {"transcript": "물을 좀 더 부었어요"}
				}
				""");
	}

	@Test
	void 조리에_쓴_개인_버전이_계보_부모가_되고_버전_번호가_쌓인다() throws Exception {
		JsonNode v1 = createVersion(submitReview(RAMEN_ID, 1), editBody("400"));

		// 부모는 본문이 아니라 "이 버전으로 조리했다"고 적힌 리뷰에서 온다.
		UUID reviewId = submitReview(RAMEN_ID, 1, UUID.fromString(v1.get("id").asString()), null);
		JsonNode v2 = createVersion(reviewId, editBody("300"));

		assertThat(v2.get("versionNumber").asInt()).isEqualTo(v1.get("versionNumber").asInt() + 1);
		assertThat(v2.get("parentVersionId").asString()).isEqualTo(v1.get("id").asString());

		// diff 는 부모 체인이 아니라 원본 기준 누적이므로 v2 는 300 만 갖는다.
		JsonNode detail = getDetail(v2.get("id").asString());
		assertThat(detail.get("ingredientAdjustments")).hasSize(1);
		assertThat(amountAt(detail.get("ingredients"), "물")).isEqualByComparingTo("300");
	}

	@Test
	void 다른_레시피의_재료를_참조하면_400() throws Exception {
		// FK 는 존재만 보므로 DB 는 이걸 통과시킨다. 서버만 잡을 수 있다.
		mockMvc.perform(postEdits(submitReview(RAMEN_ID, 1), """
						{
						  "setup": {
						    "ingredientAdjustments": [
						      {"originalIngredientId": "%s", "type": "MODIFY",
						       "amount": 200, "sortOrder": 0}
						    ],
						    "stepAdjustments": []
						  }
						}
						""".formatted(FRIED_RICE_KIMCHI)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ADD에_name이_없거나_원본을_참조하면_400() throws Exception {
		mockMvc.perform(postEdits(submitReview(RAMEN_ID, 1), """
						{
						  "setup": {
						    "ingredientAdjustments": [
						      {"type": "ADD", "amount": 1, "unit": "장", "sortOrder": 0}
						    ],
						    "stepAdjustments": []
						  }
						}
						"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(postEdits(submitReview(RAMEN_ID, 1), """
						{
						  "setup": {
						    "ingredientAdjustments": [
						      {"originalIngredientId": "%s", "type": "ADD",
						       "name": "치즈", "sortOrder": 0}
						    ],
						    "stepAdjustments": []
						  }
						}
						""".formatted(ING_EGG)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 남의_reviewId로_버전을_만들려_하면_이미_버전이_있어도_404() throws Exception {
		// 멱등 게이트가 소유자 확인보다 앞에 있으면 여기서 404 대신 남의 버전이 그대로 나간다.
		UUID reviewId = submitReview(RAMEN_ID, 1);
		createVersion(reviewId, editBody("400"));

		mockMvc.perform(postEdits(reviewId, editBody("400"))
						.header(UserService.USER_ID_HEADER, createAnonymousUser()))
				.andExpect(status().isNotFound());
	}

	@Test
	void 음수_amount나_공백_문자열은_400() throws Exception {
		// DB CHECK 는 ADD/비ADD 조합만 본다 — 값의 범위는 여기서만 잡힌다.
		mockMvc.perform(postEdits(submitReview(RAMEN_ID, 1), editBody("-100")))
				.andExpect(status().isBadRequest());

		mockMvc.perform(postEdits(submitReview(RAMEN_ID, 1), """
						{
						  "setup": {
						    "ingredientAdjustments": [
						      {"originalIngredientId": "%s", "type": "MODIFY",
						       "name": "  ", "sortOrder": 0}
						    ],
						    "stepAdjustments": []
						  }
						}
						""".formatted(ING_WATER)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(postEdits(submitReview(RAMEN_ID, 1), """
						{
						  "setup": {
						    "ingredientAdjustments": [],
						    "stepAdjustments": [
						      {"originalStepId": "%s", "type": "MODIFY",
						       "timerSeconds": -1, "sortOrder": 0}
						    ]
						  }
						}
						""".formatted(STEP_BOIL)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 같은_원본_행에_조정이_둘이면_400() throws Exception {
		// 합성은 원본 하나당 조정 하나만 읽는다 — 둘을 받으면 하나가 조용히 사라진다.
		mockMvc.perform(postEdits(submitReview(RAMEN_ID, 1), """
						{
						  "setup": {
						    "ingredientAdjustments": [
						      {"originalIngredientId": "%s", "type": "MODIFY",
						       "amount": 400, "sortOrder": 0},
						      {"originalIngredientId": "%s", "type": "REMOVE", "sortOrder": 1}
						    ],
						    "stepAdjustments": []
						  }
						}
						""".formatted(ING_WATER, ING_WATER)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 조정_목록에_null_항목이_있으면_500이_아니라_400() throws Exception {
		mockMvc.perform(postEdits(submitReview(RAMEN_ID, 2), """
						{
						  "setup": {
						    "ingredientAdjustments": [null],
						    "stepAdjustments": []
						  }
						}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 다른_레시피의_개인_버전으로_조리했다는_리뷰는_저장_단계에서_400() throws Exception {
		// 계보 부모가 리뷰에서 오므로 게이트도 리뷰 저장으로 올라갔다. 통과시키면 남의 레시피
		// 버전이 라면 버전의 부모로 박힌 채 리뷰만 남는다.
		JsonNode friedRiceVersion = createVersion(
				submitReview(TestRecipeIds.FRIED_RICE_RECIPE_ID, 1), """
				{
				  "setup": {
				    "ingredientAdjustments": [
				      {"originalIngredientId": "%s", "type": "MODIFY",
				       "amount": 200, "sortOrder": 0}
				    ],
				    "stepAdjustments": []
				  }
				}
				""".formatted(FRIED_RICE_KIMCHI));

		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(RAMEN_ID, 1,
								UUID.fromString(friedRiceVersion.get("id").asString()), null)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 없는_reviewId를_지목하면_404() throws Exception {
		mockMvc.perform(postEdits(UUID.randomUUID(), editBody("400")))
				.andExpect(status().isNotFound());
	}

	@Test
	void MODIFY_단계에_insertAfterStepIndex를_주면_400() throws Exception {
		// 앵커는 ADD 전용이라 DB CHECK 가 비ADD 에는 NULL 을 요구한다. 서버가 먼저 잡아야 400 이다.
		mockMvc.perform(postEdits(submitReview(RAMEN_ID, 1), """
						{
						  "setup": {
						    "ingredientAdjustments": [],
						    "stepAdjustments": [
						      {"originalStepId": "%s", "type": "MODIFY",
						       "insertAfterStepIndex": 5, "sortOrder": 0,
						       "instruction": "다르게 끓인다"}
						    ]
						  }
						}
						""".formatted(STEP_BOIL)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 인분을_모르는_조리로_재료를_수정하면_400() throws Exception {
		// 리뷰에 targetServings 가 없으면 되돌릴 배수를 모른다. 통과시키면 조리 인분 기준 양이
		// 1인분 기준으로 그대로 저장돼 조용히 어긋난다.
		mockMvc.perform(postEdits(submitReview(RAMEN_ID, null), editBody("2000")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 단계만_수정하면_인분을_몰라도_된다() throws Exception {
		// 타이머는 양과 무관하다 — 인분을 요구할 이유가 없다.
		JsonNode version = createVersion(submitReview(RAMEN_ID, null), """
				{
				  "setup": {
				    "ingredientAdjustments": [],
				    "stepAdjustments": [
				      {"originalStepId": "%s", "type": "MODIFY",
				       "sortOrder": 0, "timerSeconds": 240}
				    ]
				  }
				}
				""".formatted(STEP_BOIL));

		assertThat(getDetail(version.get("id").asString()).get("stepAdjustments")).hasSize(1);
	}

	/** 소유자 스코프를 확인하려면 데모 사용자가 아닌 진짜 다른 사용자 행이 있어야 한다. */
	private String createAnonymousUser() throws Exception {
		String response = mockMvc.perform(post("/api/v1/users/anonymous"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return objectMapper.readTree(response).get("id").asString();
	}

	/** 조리 기록을 먼저 남긴다 — 그 reviewId 가 버전 생성의 멱등키이자 조리 사실의 출처다. */
	private UUID submitReview(UUID recipeId, Integer targetServings) throws Exception {
		return submitReview(recipeId, targetServings, null, null);
	}

	private UUID submitReview(UUID recipeId, Integer targetServings,
			UUID sourcePersonalVersionId, String comment) throws Exception {
		String response = mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(recipeId, targetServings, sourcePersonalVersionId, comment)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return UUID.fromString(objectMapper.readTree(response).get("id").asString());
	}

	private String reviewBody(UUID recipeId, Integer targetServings,
			UUID sourcePersonalVersionId, String comment) {
		return """
				{
				  "clientSessionId": "%s",
				  "recipeId": "%s",
				  "cookedAt": "2026-07-30T01:00:00Z",
				  "targetServings": %s,
				  "sourcePersonalVersionId": %s,
				  "comment": %s,
				  "rating": 4
				}
				""".formatted(UUID.randomUUID(), recipeId,
						targetServings == null ? "null" : targetServings.toString(),
						quotedOrNull(sourcePersonalVersionId),
						quotedOrNull(comment));
	}

	private String quotedOrNull(Object value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private String editBody(String waterAmount) {
		return """
				{
				  "setup": {
				    "ingredientAdjustments": [
				      {"originalIngredientId": "%s", "type": "MODIFY",
				       "amount": %s, "sortOrder": 0}
				    ],
				    "stepAdjustments": []
				  }
				}
				""".formatted(ING_WATER, waterAmount);
	}

	private MockHttpServletRequestBuilder postEdits(UUID reviewId, String body) {
		return post("/api/v1/reviews/" + reviewId + "/personal-versions")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	private JsonNode createVersion(UUID reviewId, String body) throws Exception {
		String response = mockMvc.perform(postEdits(reviewId, body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return objectMapper.readTree(response);
	}

	/** 수정이 결과적으로 원본과 같으면 204 이고 버전 행이 생기지 않는다. */
	private void expectNoVersion(UUID reviewId, String body) throws Exception {
		mockMvc.perform(postEdits(reviewId, body))
				.andExpect(status().isNoContent());
	}

	private JsonNode getDetail(String versionId) throws Exception {
		String response = mockMvc.perform(get("/api/v1/personal-versions/" + versionId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return objectMapper.readTree(response);
	}

	private java.util.List<String> namesOf(JsonNode ingredients) {
		return ingredients.valueStream().map(node -> node.get("name").asString()).toList();
	}

	private String originAt(JsonNode ingredients, String name) {
		return findByName(ingredients, name).get("origin").asString();
	}

	private java.math.BigDecimal amountAt(JsonNode ingredients, String name) {
		return findByName(ingredients, name).get("amount").decimalValue();
	}

	private JsonNode findByName(JsonNode ingredients, String name) {
		return ingredients.valueStream()
				.filter(node -> name.equals(node.get("name").asString()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("재료가 없다: " + name));
	}
}
