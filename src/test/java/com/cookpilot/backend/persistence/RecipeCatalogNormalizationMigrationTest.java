package com.cookpilot.backend.persistence;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;

import com.cookpilot.backend.PostgresApiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RecipeCatalogNormalizationMigrationTest extends PostgresApiTestBase {

	private static final UUID NORMALIZATION_RECIPE_ID =
			UUID.fromString("f0000000-0000-0000-0000-000000000001");
	private static final UUID DUPLICATE_RECIPE_ID =
			UUID.fromString("1fa07d9c-c55a-556a-be1b-1c7539bc003e");
	private static final UUID UPSCALED_RECIPE_ID =
			UUID.fromString("a32d03c6-9e44-559f-a7eb-76fd96f74887");

	@Autowired
	private DataSource dataSource;

	@Test
	void 카탈로그_정규화는_명확한_수량과_대표이미지만_바꾸고_단계이미지는_보존한다() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("""
				INSERT INTO recipes (id, title, status, image_url)
				VALUES (?, '정규화 회귀 검증', 'active',
				        'https://example.test/common/ecmFileView.do?file=ingredients')
				""", NORMALIZATION_RECIPE_ID);
		jdbc.update("""
				INSERT INTO recipe_steps (id, recipe_id, step_index, instruction, image_url)
				VALUES
				  ('f1000000-0000-0000-0000-000000000001', ?, 0, '손질한다.', 'https://example.test/step-1.jpg'),
				  ('f1000000-0000-0000-0000-000000000002', ?, 1, '완성한다.', 'https://example.test/step-2.jpg')
				""", NORMALIZATION_RECIPE_ID, NORMALIZATION_RECIPE_ID);
		insertIngredient(jdbc, "f2000000-0000-0000-0000-000000000001", "가지(90g)", 0);
		insertIngredient(jdbc, "f2000000-0000-0000-0000-000000000002", "간장(1Ts)", 1);
		insertIngredient(jdbc, "f2000000-0000-0000-0000-000000000003", "식초(2ts)", 2);
		insertIngredient(jdbc, "f2000000-0000-0000-0000-000000000004", "물(300㎖)", 3);
		insertIngredient(jdbc, "f2000000-0000-0000-0000-000000000005", "소금(약간)", 4);
		insertIngredient(jdbc, "f2000000-0000-0000-0000-000000000006", "육수(100~200ml)", 5);
		insertIngredient(jdbc, "f2000000-0000-0000-0000-000000000007", "물(1,000ml)", 6);
		insertIngredient(jdbc, "f2000000-0000-0000-0000-000000000008", "레몬즙(1,5ml)", 7);

		jdbc.update("""
				INSERT INTO recipes (id, title, status)
				VALUES (?, '중복 숨김 회귀 검증', 'active')
				""", DUPLICATE_RECIPE_ID);
		jdbc.update("""
				INSERT INTO recipes (id, title, status, image_url)
				VALUES (?, '업스케일 이미지 회귀 검증', 'active', 'https://example.test/low-resolution.png')
				""", UPSCALED_RECIPE_ID);

		ResourceDatabasePopulator migration = new ResourceDatabasePopulator(
				new ClassPathResource("db/migration/V14__normalize_recipe_catalog.sql"));
		migration.execute(dataSource);

		assertIngredient(jdbc, "f2000000-0000-0000-0000-000000000001", "가지", "90", "g");
		assertIngredient(jdbc, "f2000000-0000-0000-0000-000000000002", "간장", "1", "큰술");
		assertIngredient(jdbc, "f2000000-0000-0000-0000-000000000003", "식초", "2", "작은술");
		assertIngredient(jdbc, "f2000000-0000-0000-0000-000000000004", "물", "300", "ml");
		assertIngredient(jdbc, "f2000000-0000-0000-0000-000000000005", "소금", null, "약간");
		assertIngredient(jdbc, "f2000000-0000-0000-0000-000000000006", "육수(100~200ml)", null, null);
		assertIngredient(jdbc, "f2000000-0000-0000-0000-000000000007", "물(1,000ml)", null, null);
		assertIngredient(jdbc, "f2000000-0000-0000-0000-000000000008", "레몬즙", "1.5", "ml");

		assertThat(jdbc.queryForObject(
				"SELECT status FROM recipes WHERE id = ?", String.class, DUPLICATE_RECIPE_ID))
				.isEqualTo("inactive");
		assertThat(jdbc.queryForObject(
				"SELECT image_url FROM recipes WHERE id = ?", String.class, NORMALIZATION_RECIPE_ID))
				.isEqualTo("https://example.test/step-2.jpg");
		assertThat(jdbc.queryForList(
				"SELECT image_url FROM recipe_steps WHERE recipe_id = ? ORDER BY step_index", String.class,
				NORMALIZATION_RECIPE_ID))
				.containsExactly("https://example.test/step-1.jpg", "https://example.test/step-2.jpg");
		assertThat(jdbc.queryForObject(
				"SELECT image_url FROM recipes WHERE id = ?", String.class, UPSCALED_RECIPE_ID))
				.isEqualTo("https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/"
						+ "review-photos/catalog-recipes/eggplant-tangsuyuk.png");
	}

	private void insertIngredient(JdbcTemplate jdbc, String id, String name, int sortOrder) {
		jdbc.update("""
				INSERT INTO recipe_ingredients (id, recipe_id, name, sort_order)
				VALUES (?::uuid, ?, ?, ?)
				""", id, NORMALIZATION_RECIPE_ID, name, sortOrder);
	}

	private void assertIngredient(
			JdbcTemplate jdbc,
			String id,
			String expectedName,
			String expectedAmount,
			String expectedUnit) {
		Map<String, Object> actual = jdbc.queryForMap("""
				SELECT name, amount, unit
				FROM recipe_ingredients
				WHERE id = ?::uuid
				""", id);

		assertThat(actual.get("name")).isEqualTo(expectedName);
		if (expectedAmount == null) {
			assertThat(actual.get("amount")).isNull();
		} else {
			assertThat((BigDecimal) actual.get("amount")).isEqualByComparingTo(expectedAmount);
		}
		assertThat(actual.get("unit")).isEqualTo(expectedUnit);
	}
}
