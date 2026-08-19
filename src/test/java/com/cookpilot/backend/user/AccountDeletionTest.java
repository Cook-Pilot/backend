package com.cookpilot.backend.user;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계정 삭제(#79) 검증. 방침 제4조(후기·사진까지 삭제, 익명 보존 없음)·제9조(한 번에 삭제)가
 * 약속하는 동작을 고정한다.
 *
 * DEMO_USER_ID 는 컨텍스트를 공유하는 다른 테스트 클래스들이 쓰므로 지우지 않는다 —
 * 각 테스트가 익명 사용자를 새로 발급해 그 계정으로 삭제를 돌린다.
 */
class AccountDeletionTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String createUser() throws Exception {
		String body = mockMvc.perform(post("/api/v1/users/anonymous"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("id").asText();
	}

	@Test
	void 삭제하면_계정과_연관_데이터가_전부_사라지고_익명_행도_남지_않는다() throws Exception {
		String userId = createUser();

		// 후기(목 사진 키 포함)와 즐겨찾기를 만들어 둔다.
		String review = """
				{
				  "clientSessionId": "%s",
				  "recipeId": "%s",
				  "rating": 5,
				  "comment": "탈퇴 테스트용 후기",
				  "photoUrls": ["mock://review-photo/%s/some-photo.jpg"]
				}
				""".formatted(UUID.randomUUID(), TestRecipeIds.BRAISED_TOFU_RECIPE_ID, userId);
		mockMvc.perform(post("/api/v1/reviews")
						.header(UserService.USER_ID_HEADER, userId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(review))
				.andExpect(status().isCreated());
		mockMvc.perform(put("/api/v1/recipes/" + TestRecipeIds.BRAISED_TOFU_RECIPE_ID + "/favorite")
						.header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().is2xxSuccessful());

		mockMvc.perform(delete("/api/v1/users/me")
						.header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isNoContent());

		// 계정 행이 없다.
		assertThat(count("SELECT count(*) FROM users WHERE id = ?::uuid", userId)).isZero();
		// 후기가 SET NULL 로 익명화되어 남지 않고 통째로 사라졌다(방침 제4조).
		assertThat(count("SELECT count(*) FROM post_cook_reviews WHERE comment = '탈퇴 테스트용 후기'"))
				.isZero();
		assertThat(count("SELECT count(*) FROM recipe_favorites WHERE user_id = ?::uuid", userId))
				.isZero();
		// 탈퇴 기록은 남는다(방침 제8조 — 복원 시 재적용 목록).
		assertThat(count("SELECT count(*) FROM account_deletions WHERE user_id = ?::uuid", userId))
				.isOne();
	}

	@Test
	void 삭제_후_재호출과_개인화_API는_404_이지_500_이_아니다() throws Exception {
		String userId = createUser();

		mockMvc.perform(delete("/api/v1/users/me").header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isNoContent());

		// 멱등: 같은 세션으로 다시 지워도 404.
		mockMvc.perform(delete("/api/v1/users/me").header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

		// 아직 살아 있는 세션(삭제된 유저)의 개인화 조회도 404.
		mockMvc.perform(get("/api/v1/users/me").header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void 소셜_연결이_없는_계정도_삭제된다() throws Exception {
		// 익명 계정은 provider 가 null — unlink 분기가 NPE 없이 지나가는지 고정한다.
		String userId = createUser();
		mockMvc.perform(delete("/api/v1/users/me").header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isNoContent());
		assertThat(count("SELECT count(*) FROM users WHERE id = ?::uuid", userId)).isZero();
	}

	private long count(String sql, Object... args) {
		Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}
}
