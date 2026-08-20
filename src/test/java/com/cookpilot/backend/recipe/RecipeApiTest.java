package com.cookpilot.backend.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 레시피 목록/상세 API. 원본 레시피와 개인 버전 배지를 모두 PostgreSQL/JPA에서 조회한다.
 */
class RecipeApiTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext applicationContext;

	@Autowired
	private RecipeRepository recipeRepository;

	@Autowired
	private RecipeIngredientRepository ingredientRepository;

	@Autowired
	private IngredientRepository masterIngredientRepository;

	@Autowired
	private RecipeStepRepository stepRepository;

	@Autowired
	private RecipeService recipeService;

	@Test
	void 레시피_목록을_조회한다() throws Exception {
		mockMvc.perform(get("/api/v1/recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
				.andExpect(jsonPath("$[0].id").exists())
				.andExpect(jsonPath("$[0].title").exists())
				.andExpect(jsonPath("$[0].hasPersonalVersion").exists())
				.andExpect(jsonPath("$[0].favorite").exists())
				.andExpect(jsonPath("$[0].ingredients").doesNotExist())
				.andExpect(jsonPath("$[0].steps").doesNotExist());
	}

	@Test
	void 제목이_같은_레시피는_ID_순서로_반환한다() {
		recipeRepository.save(new RecipeEntity(
				"동일 제목 정렬 검증", "첫 번째", BigDecimal.ONE));
		recipeRepository.save(new RecipeEntity(
				"동일 제목 정렬 검증", "두 번째", BigDecimal.ONE));

		List<String> actual = recipeService.findAll().stream()
				.filter(recipe -> recipe.title().equals("동일 제목 정렬 검증"))
				.map(RecipeOverview::id)
				.map(UUID::toString)
				.toList();
		List<String> expected = actual.stream().sorted().toList();

		org.assertj.core.api.Assertions.assertThat(actual)
				.containsExactlyElementsOf(expected);
	}

	@Test
	void 카탈로그의_필수_재료와_조리_안내가_일치한다() throws Exception {
		mockMvc.perform(get("/api/v1/recipes/10000000-0000-0000-0000-000000000003"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ingredients[?(@.name == '식용유')].required")
						.value(contains(true)))
				.andExpect(jsonPath("$.steps[1].instruction")
						.value("팬에 식용유를 두르고 두부를 올려 앞뒤로 노릇하게 구우세요."));

		mockMvc.perform(get("/api/v1/recipes/10000000-0000-0000-0000-000000000008"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ingredients[?(@.name == '양배추')].required")
						.value(contains(true)))
				.andExpect(jsonPath("$.ingredients[?(@.name == '고구마')].required")
						.value(contains(true)));
	}

	@Test
	void 레시피_상세를_조회한다() throws Exception {
		mockMvc.perform(get("/api/v1/recipes/" + TestRecipeIds.RAMEN_RECIPE_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("라면"))
				.andExpect(jsonPath("$.baseServings").value(1.0))
				.andExpect(jsonPath("$.steps", hasSize(3)))
				.andExpect(jsonPath("$.steps[0].instruction").value("물 500ml를 넣고 3분간 끓이세요."))
				.andExpect(jsonPath("$.steps[0].timerSeconds").value(180))
				.andExpect(jsonPath("$.ingredients", hasSize(4)));
	}

	@Test
	void DB에만_저장한_레시피를_목록과_상세에서_조회한다() throws Exception {
		RecipeEntity recipe = recipeRepository.save(new RecipeEntity(
				"DB 전용 된장국", "하드코딩 Map에는 없는 레시피", BigDecimal.valueOf(2),
				"https://cdn.cookpilot.app/recipes/doenjang.png"));
		ingredientRepository.save(new RecipeIngredientEntity(
				recipe.getId(), ingredient("물"), BigDecimal.valueOf(500), "ml", true, 1));
		ingredientRepository.save(new RecipeIngredientEntity(
				recipe.getId(), ingredient("된장"), BigDecimal.ONE, "큰술", true, 0));
		stepRepository.save(new RecipeStepEntity(
				recipe.getId(), 1, "두부를 넣고 마저 끓여요.", 180, "냄비가 뜨거워요"));
		stepRepository.save(new RecipeStepEntity(
				recipe.getId(), 0, "물을 끓이고 된장을 풀어요.", 120, null,
				"https://cdn.cookpilot.app/steps/doenjang-1.png"));

		mockMvc.perform(get("/api/v1/recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '" + recipe.getId() + "')]", hasSize(1)))
				.andExpect(jsonPath("$[?(@.id == '" + recipe.getId() + "')].imageUrl")
						.value(contains("https://cdn.cookpilot.app/recipes/doenjang.png")));

		mockMvc.perform(get("/api/v1/recipes/" + recipe.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("DB 전용 된장국"))
				.andExpect(jsonPath("$.description").value("하드코딩 Map에는 없는 레시피"))
				.andExpect(jsonPath("$.imageUrl").value("https://cdn.cookpilot.app/recipes/doenjang.png"))
				.andExpect(jsonPath("$.ingredients[0].name").value("된장"))
				.andExpect(jsonPath("$.ingredients[1].name").value("물"))
				.andExpect(jsonPath("$.steps[0].instruction").value("물을 끓이고 된장을 풀어요."))
				.andExpect(jsonPath("$.steps[0].timerSeconds").value(120))
				.andExpect(jsonPath("$.steps[0].imageUrl")
						.value("https://cdn.cookpilot.app/steps/doenjang-1.png"))
				.andExpect(jsonPath("$.steps[1].cautionNote").value("냄비가 뜨거워요"));
	}

	@Test
	void 요리명과_재료명으로_레시피를_페이지_검색한다() throws Exception {
		RecipeEntity basilPasta = recipeRepository.save(new RecipeEntity(
				"검색전용 바질 파스타", "요리명 검색 검증", BigDecimal.valueOf(2)));
		ingredientRepository.save(new RecipeIngredientEntity(
				basilPasta.getId(), ingredient("생바질"), BigDecimal.valueOf(10), "g", true, 0));
		RecipeEntity tomatoSoup = recipeRepository.save(new RecipeEntity(
				"검색전용 토마토 수프", "재료명 검색 검증", BigDecimal.valueOf(2)));
		ingredientRepository.save(new RecipeIngredientEntity(
				tomatoSoup.getId(), ingredient("완숙 토마토"), BigDecimal.valueOf(2), "개", true, 0));
		RecipeEntity inactiveBasil = new RecipeEntity(
				"검색전용 바질 폐기본", "중복 숨김 검증", BigDecimal.valueOf(2));
		inactiveBasil.setStatus("inactive");
		inactiveBasil = recipeRepository.save(inactiveBasil);
		ingredientRepository.save(new RecipeIngredientEntity(
				inactiveBasil.getId(), ingredient("생바질"), BigDecimal.ONE, "g", true, 0));

		mockMvc.perform(get("/api/v1/recipes/search")
				.param("title", "바질 파스타")
				.param("page", "1")
				.param("size", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].id").value(basilPasta.getId().toString()))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.totalItems").value(1));

		mockMvc.perform(get("/api/v1/recipes/search")
				.param("ingredient", "토마토")
				.param("page", "1")
				.param("size", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(tomatoSoup.getId().toString()));

		mockMvc.perform(get("/api/v1/recipes/search")
				.param("title", "검색전용")
				.param("ingredient", "바질")
				.param("page", "1")
				.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].id").value(basilPasta.getId().toString()))
				.andExpect(jsonPath("$.totalPages").value(1));

		mockMvc.perform(get("/api/v1/recipes/search")
				.param("title", "검색전용")
				.param("page", "999")
				.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.page").value(2))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.totalItems").value(2));

		mockMvc.perform(get("/api/v1/recipes/search")
				.param("title", "검색결과없음")
				.param("page", "999")
				.param("size", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(0)))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.totalPages").value(0))
				.andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void 검색어의_LIKE_기호는_와일드카드가_아닌_문자로_검색한다() throws Exception {
		RecipeEntity literalSymbols = recipeRepository.save(new RecipeEntity(
				"검색기호 %_ 레시피", "LIKE 기호 검색 검증", BigDecimal.ONE));
		ingredientRepository.save(new RecipeIngredientEntity(
				literalSymbols.getId(), ingredient("100%_카카오"), BigDecimal.ONE, "조각", true, 0));

		mockMvc.perform(get("/api/v1/recipes/search")
				.param("title", "%_")
				.param("size", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].id").value(literalSymbols.getId().toString()));

		mockMvc.perform(get("/api/v1/recipes/search")
				.param("ingredient", "%_")
				.param("size", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].id").value(literalSymbols.getId().toString()));
	}

	@Test
	void 없는_레시피는_404를_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/recipes/99999999-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 상세_응답에_원본_출처가_나온다() throws Exception {
		// #85: 원본에서 끌고 온 레시피는 어디서 왔는지 API 로 바로 확인할 수 있어야 한다.
		RecipeEntity sourced = new RecipeEntity(
				"출처 확인용 레시피", "원본 추적 검증", BigDecimal.ONE);
		sourced.setSourceType("COOKRCP01");
		sourced.setSourceRef("28");
		sourced = recipeRepository.save(sourced);

		mockMvc.perform(get("/api/v1/recipes/" + sourced.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sourceType").value("COOKRCP01"))
				.andExpect(jsonPath("$.sourceRef").value("28"));

		// 손으로 만든 레시피(시드)는 출처가 비어 있다.
		mockMvc.perform(get("/api/v1/recipes/" + TestRecipeIds.RAMEN_RECIPE_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sourceType").isEmpty())
				.andExpect(jsonPath("$.sourceRef").isEmpty());
	}

	@Test
	void 출처는_쌍으로만_저장되고_같은_원본은_두_번_못_들어온다() throws Exception {
		// 쌍 CHECK: type 만 있으면 거부
		RecipeEntity half = new RecipeEntity("출처 반쪽", null, BigDecimal.ONE);
		half.setSourceType("COOKRCP01");
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> recipeRepository.saveAndFlush(half))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

		// 부분 UNIQUE: 같은 (type, ref) 재적재 거부
		RecipeEntity first = new RecipeEntity("출처 중복 1", null, BigDecimal.ONE);
		first.setSourceType("COOKRCP01");
		first.setSourceRef("777");
		recipeRepository.saveAndFlush(first);

		RecipeEntity second = new RecipeEntity("출처 중복 2", null, BigDecimal.ONE);
		second.setSourceType("COOKRCP01");
		second.setSourceRef("777");
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> recipeRepository.saveAndFlush(second))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	void 게스트도_레시피_목록_검색_상세를_볼_수_있다() throws Exception {
		// 기본 MockMvc 는 데모 사용자 토큰을 실어 보내므로, 헤더가 아예 없는
		// 게스트 요청은 기본값 없는 MockMvc 를 따로 만들어 보낸다.
		var guest = MockMvcBuilders.webAppContextSetup(applicationContext).build();

		guest.perform(get("/api/v1/recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
				// 세션이 없으면 개인화 플래그는 기본값이다.
				.andExpect(jsonPath("$[0].favorite").value(false))
				.andExpect(jsonPath("$[0].hasPersonalVersion").value(false));

		guest.perform(get("/api/v1/recipes/search")
				.param("title", "라면"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].favorite").value(false));

		guest.perform(get("/api/v1/recipes/" + TestRecipeIds.RAMEN_RECIPE_ID))
				.andExpect(status().isOk());
	}

	@Test
	void 비어_있는_인증_헤더는_게스트가_아니라_401이다() throws Exception {
		// 게스트는 '헤더 없음'뿐이다. 헤더가 있는데 비었으면 클라이언트 버그이므로
		// 조용히 게스트 데이터를 주지 않고 401 로 알린다.
		var guest = MockMvcBuilders.webAppContextSetup(applicationContext).build();

		guest.perform(get("/api/v1/recipes")
				.header(org.springframework.http.HttpHeaders.AUTHORIZATION, " "))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 게스트의_쓰기_요청은_여전히_거부된다() throws Exception {
		// 열람만 열렸다는 경계 확인 — 즐겨찾기 추가는 세션 없이는 401 이어야 한다.
		var guest = MockMvcBuilders.webAppContextSetup(applicationContext).build();

		guest.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.put("/api/v1/recipes/" + TestRecipeIds.RAMEN_RECIPE_ID + "/favorite"))
				.andExpect(status().isUnauthorized());
	}

	/** ingredients 마스터에서 이름으로 찾거나 만든다 — 컨테이너가 클래스 간 공유라 UNIQUE 충돌 방지. */
	private IngredientEntity ingredient(String name) {
		return masterIngredientRepository.findByName(name)
				.orElseGet(() -> masterIngredientRepository.save(new IngredientEntity(name)));
	}
}
