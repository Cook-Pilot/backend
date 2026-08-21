package com.cookpilot.backend.tag;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;

/**
 * 태그 필터의 계약.
 *
 * 지키려는 규칙은 하나다 — **축 안은 OR, 축 사이는 AND**(V14). 이걸 틀리면 칩을 두 개
 * 골랐을 때 결과가 늘어나거나(잘못된 OR) 아무것도 안 나온다(잘못된 AND).
 *
 * 컨테이너·컨텍스트를 공유해 다른 클래스의 데이터가 남으므로, 이 클래스 전용 제목 접두사로
 * 범위를 좁혀 개수를 단언한다.
 */
class TagFilterApiTest extends PostgresApiTestBase {

	private static final String PREFIX = "태그필터-";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void clearOwnRecipes() {
		jdbc.update("DELETE FROM recipes WHERE title LIKE ?", PREFIX + "%");
	}

	private void recipe(String name, String... tagCodes) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"INSERT INTO recipes (id, title, description, base_servings) VALUES (?, ?, '', 1)",
				id, PREFIX + name);
		for (String code : tagCodes) {
			jdbc.update("""
					INSERT INTO recipe_tags (recipe_id, tag_code, axis_code, assigned_by)
					SELECT ?, code, axis_code, 'MANUAL' FROM tags WHERE code = ?
					""", id, code);
		}
	}

	@Test
	void 같은_축의_태그_둘은_OR_로_묶인다() throws Exception {
		recipe("굽기", "METHOD_GRILL");
		recipe("끓이기", "METHOD_BOIL");
		recipe("볶기", "METHOD_STIR_FRY");

		mockMvc.perform(get("/api/v1/recipes/search")
						.param("title", PREFIX)
						.param("tags", "METHOD_GRILL")
						.param("tags", "METHOD_BOIL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(2));
	}

	@Test
	void 다른_축의_태그는_AND_로_묶인다() throws Exception {
		recipe("굽기반찬", "METHOD_GRILL", "DISH_SIDE");
		recipe("굽기밥", "METHOD_GRILL", "DISH_RICE");
		recipe("끓이기반찬", "METHOD_BOIL", "DISH_SIDE");

		mockMvc.perform(get("/api/v1/recipes/search")
						.param("title", PREFIX)
						.param("tags", "METHOD_GRILL")
						.param("tags", "DISH_SIDE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(1))
				.andExpect(jsonPath("$.items[0].title").value(PREFIX + "굽기반찬"));
	}

	@Test
	void 축이_섞이면_축_안은_OR_축_사이는_AND_다() throws Exception {
		recipe("굽기반찬", "METHOD_GRILL", "DISH_SIDE");
		recipe("끓이기반찬", "METHOD_BOIL", "DISH_SIDE");
		recipe("볶기반찬", "METHOD_STIR_FRY", "DISH_SIDE");
		recipe("굽기밥", "METHOD_GRILL", "DISH_RICE");

		mockMvc.perform(get("/api/v1/recipes/search")
						.param("title", PREFIX)
						.param("tags", "METHOD_GRILL")
						.param("tags", "METHOD_BOIL")
						.param("tags", "DISH_SIDE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(2));
	}

	@Test
	void 태그를_주지_않으면_거르지_않는다() throws Exception {
		recipe("태그없음");
		recipe("굽기", "METHOD_GRILL");

		mockMvc.perform(get("/api/v1/recipes/search").param("title", PREFIX))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(2));
	}

	@Test
	void 태그와_재료_조건은_함께_걸린다() throws Exception {
		recipe("굽기", "METHOD_GRILL");

		mockMvc.perform(get("/api/v1/recipes/search")
						.param("title", PREFIX)
						.param("tags", "METHOD_GRILL")
						.param("ingredient", "존재하지않는재료"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void 없는_태그_코드는_400_이다() throws Exception {
		// 조용히 무시하면 오타 하나가 '필터가 안 걸린 전체 목록'이 되어 성공으로 읽힌다.
		mockMvc.perform(get("/api/v1/recipes/search").param("tags", "METHOD_GRILLL"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 태그_사전을_내려준다() throws Exception {
		mockMvc.perform(get("/api/v1/tags"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].code").isNotEmpty())
				.andExpect(jsonPath("$[0].label").isNotEmpty())
				// 축은 내려보내지 않는다 — 필터 의미는 서버가 계산한다.
				.andExpect(jsonPath("$[0].axisCode").doesNotExist());
	}

	@Test
	void 태그_사전은_로그인_없이도_볼_수_있다() throws Exception {
		mockMvc.perform(get("/api/v1/tags").header("Authorization", ""))
				.andExpect(status().isOk());
	}
}
