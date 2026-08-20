package com.cookpilot.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V21 백필이 원문 분류를 태그로 옮기는 규칙의 적용 테스트.
 *
 * 마이그레이션은 기동 시 이미 한 번 돌았고, 그때 이 테스트의 레시피는 없었다.
 * 그래서 **마이그레이션 파일을 그대로 다시 읽어 실행한다** — SQL 을 테스트에 옮겨 적으면
 * 정작 배포되는 파일이 바뀌어도 테스트는 통과한다. 재실행은 안전하다(ON CONFLICT DO NOTHING).
 *
 * 특히 지키려는 것은 라벨 매핑이다. 원문은 '국&찌개' 인데 사전은 '국·찌개' 라 글자가 다르고,
 * 매핑을 빠뜨리면 101행이 조용히 미부여로 남는다.
 *
 * 실행 요건: Docker 데몬 필요(Testcontainers).
 */
@SpringBootTest
@ActiveProfiles("db")
@Testcontainers
@Transactional
class RecipeTagImportBackfillTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired
	private JdbcTemplate jdbc;

	private UUID insertRecipe(String title, String description) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"INSERT INTO recipes (id, title, description, base_servings) VALUES (?, ?, ?, 1)",
				id, title, description);
		return id;
	}

	/** 배포되는 마이그레이션 파일을 그대로 실행한다. */
	private void runBackfill() {
		String sql;
		try {
			sql = new String(
					new ClassPathResource("db/migration/V21__backfill_recipe_tags_from_import.sql")
							.getInputStream().readAllBytes(),
					StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException("V21 마이그레이션 파일을 읽지 못했다", e);
		}
		jdbc.execute(sql);
	}

	private List<String> tagsOf(UUID recipeId) {
		return jdbc.queryForList(
				"SELECT tag_code FROM recipe_tags WHERE recipe_id = ? ORDER BY tag_code",
				String.class, recipeId);
	}

	@Test
	void 한_줄에_있는_조리방법과_요리종류를_각각_옮긴다() {
		UUID id = insertRecipe("백필 대상",
				"월계수잎으로 깊은 맛을 냈어요.\n"
				+ "조리방법: 굽기 | 요리종류: 반찬\n"
				+ "1인분 310.8kcal · 나트륨 301.7mg");

		runBackfill();

		assertThat(tagsOf(id)).containsExactly("DISH_SIDE", "METHOD_GRILL");
	}

	@Test
	void 원문_국_찌개는_사전_라벨이_달라도_이어진다() {
		// 원문 '국&찌개' vs 사전 '국·찌개'. 매핑을 빠뜨리면 여기서 걸린다.
		UUID id = insertRecipe("국찌개 대상", "조리방법: 끓이기 | 요리종류: 국&찌개");

		runBackfill();

		assertThat(tagsOf(id)).containsExactly("DISH_SOUP_STEW", "METHOD_BOIL");
	}

	@Test
	void 기타는_사전에_태그가_없으므로_부여하지_않는다() {
		UUID id = insertRecipe("기타 대상", "조리방법: 기타 | 요리종류: 기타");

		runBackfill();

		assertThat(tagsOf(id)).isEmpty();
	}

	@Test
	void 한쪽_축만_기타면_나머지_축은_부여한다() {
		UUID id = insertRecipe("한쪽만 기타", "조리방법: 기타 | 요리종류: 밥");

		runBackfill();

		assertThat(tagsOf(id)).containsExactly("DISH_RICE");
	}

	@Test
	void 분류_줄이_없는_시드_레시피는_건드리지_않는다() {
		UUID id = insertRecipe("손으로 만든 레시피", "설명만 있고 분류 줄이 없다.");

		runBackfill();

		assertThat(tagsOf(id)).isEmpty();
	}

	@Test
	void 두_번_돌려도_행이_늘지_않는다() {
		UUID id = insertRecipe("재실행 대상", "조리방법: 볶기 | 요리종류: 일품");

		runBackfill();
		runBackfill();

		assertThat(tagsOf(id)).containsExactly("DISH_MAIN", "METHOD_STIR_FRY");
	}

	@Test
	void 부여_출처는_IMPORT_로_남는다() {
		// 사람이 고른 것도 규칙이 유도한 것도 아니고 적재된 원문에 있던 값이다.
		UUID id = insertRecipe("출처 확인", "조리방법: 찌기 | 요리종류: 후식");

		runBackfill();

		assertThat(jdbc.queryForList(
				"SELECT DISTINCT assigned_by FROM recipe_tags WHERE recipe_id = ?",
				String.class, id)).containsExactly("IMPORT");
	}
}
