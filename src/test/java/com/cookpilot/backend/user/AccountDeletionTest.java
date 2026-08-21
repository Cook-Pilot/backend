package com.cookpilot.backend.user;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;

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
 * 각 테스트가 별도 계정({@link #createTestUser()})을 만들어 그 계정으로 삭제를 돌린다.
 */
class AccountDeletionTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 삭제하면_계정과_연관_데이터가_전부_사라지고_익명_행도_남지_않는다() throws Exception {
		UUID userId = createTestUser();
		String bearer = bearerFor(userId);

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
						.header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(review))
				.andExpect(status().isCreated());
		mockMvc.perform(put("/api/v1/recipes/" + TestRecipeIds.BRAISED_TOFU_RECIPE_ID + "/favorite")
						.header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().is2xxSuccessful());

		mockMvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
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
		UUID userId = createTestUser();
		String bearer = bearerFor(userId);

		mockMvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isNoContent());

		// 멱등: 같은 세션으로 다시 지워도 404.
		mockMvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

		// 아직 살아 있는 세션(삭제된 유저)의 개인화 조회도 404.
		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void 해제기가_없는_제공자의_계정도_삭제된다() throws Exception {
		// createTestUser() 는 provider=DEV — SocialUnlinker 가 없는 제공자라 unlink 분기를
		// 건너뛰고도 삭제가 끝나는지 고정한다(해제기는 카카오뿐이다).
		UUID userId = createTestUser();
		mockMvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearerFor(userId)))
				.andExpect(status().isNoContent());
		assertThat(count("SELECT count(*) FROM users WHERE id = ?::uuid", userId)).isZero();
	}

	@Test
	void 유효한_세션_토큰_없이는_삭제되지_않는다() throws Exception {
		// 비가역 삭제라 식별자만으로 통과되면 안 된다(리뷰 P1). 위조 토큰은 401 이고 계정은 그대로다.
		UUID userId = createTestUser();
		mockMvc.perform(delete("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
				.andExpect(status().isUnauthorized());
		assertThat(count("SELECT count(*) FROM users WHERE id = ?::uuid", userId)).isOne();
	}

	private long count(String sql, Object... args) {
		Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}
}
