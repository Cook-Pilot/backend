package com.cookpilot.backend.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CookingResultStorageMigrationUpgradeTest {

	private static final UUID USER_ID =
			UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID RECIPE_ID =
			UUID.fromString("10000000-0000-0000-0000-000000000002");
	private static final UUID LEGACY_REVIEW_ID = UUID.randomUUID();
	private static final UUID LEGACY_SESSION_ID = UUID.randomUUID();
	private static final Instant LEGACY_COOKED_AT =
			Instant.parse("2026-07-01T01:02:03.123456Z");
	private static final UUID OLD_BINARY_REVIEW_ID = UUID.randomUUID();
	private static final UUID OLD_BINARY_SESSION_ID = UUID.randomUUID();
	private static final Instant OLD_BINARY_COOKED_AT =
			Instant.parse("2026-07-02T04:05:06.123456Z");
	private static final String VALID_FINGERPRINT = "a".repeat(64);

	@Container
	private static final PostgreSQLContainer POSTGRES =
			new PostgreSQLContainer("postgres:16-alpine");

	@Test
	void V9_레거시_행을_보존하고_완료결과_bundle_제약을_검증한다()
			throws SQLException {
		flywayTo("9").migrate();
		insertLegacyReview(
				LEGACY_REVIEW_ID,
				LEGACY_SESSION_ID,
				LEGACY_COOKED_AT);

		flywayTo("10").migrate();
		// V10 배포 중 구버전 서버는 새 컬럼을 모른 채 기존 INSERT를 계속한다.
		insertLegacyReview(
				OLD_BINARY_REVIEW_ID,
				OLD_BINARY_SESSION_ID,
				OLD_BINARY_COOKED_AT);
		try (Connection connection = POSTGRES.createConnection("")) {
			assertLegacyReviewPreserved(
					connection,
					LEGACY_REVIEW_ID,
					LEGACY_SESSION_ID,
					LEGACY_COOKED_AT);
			assertLegacyReviewPreserved(
					connection,
					OLD_BINARY_REVIEW_ID,
					OLD_BINARY_SESSION_ID,
					OLD_BINARY_COOKED_AT);
			assertValidStatesAccepted(connection);
			assertInvalidRowsRejected(connection);
			assertNonFinalizedReviewDataRejected(connection);
			assertConstraintValidationState(connection, false);
			assertMigrationSucceeded(connection, "10");
		}

		flywayTo("11").migrate();
		try (Connection connection = POSTGRES.createConnection("")) {
			assertLegacyReviewPreserved(
					connection,
					LEGACY_REVIEW_ID,
					LEGACY_SESSION_ID,
					LEGACY_COOKED_AT);
			assertLegacyReviewPreserved(
					connection,
					OLD_BINARY_REVIEW_ID,
					OLD_BINARY_SESSION_ID,
					OLD_BINARY_COOKED_AT);
			assertConstraintValidationState(connection, true);
			assertMigrationSucceeded(connection, "11");
		}
	}

	private Flyway flywayTo(String target) {
		var configuration = Flyway.configure()
				.dataSource(
						POSTGRES.getJdbcUrl(),
						POSTGRES.getUsername(),
						POSTGRES.getPassword());
		if (target != null) {
			configuration.target(target);
		}
		return configuration.load();
	}

	private void insertLegacyReview(
			UUID reviewId,
			UUID clientSessionId,
			Instant cookedAt) throws SQLException {
		try (Connection connection = POSTGRES.createConnection("");
				PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO post_cook_reviews (
						  id,
						  user_id,
						  recipe_id,
						  rating,
						  comment,
						  next_time_note,
						  structured_feedback,
						  created_at,
						  client_session_id,
						  cooked_at,
						  source_personal_version_id,
						  target_servings
						) VALUES (?, ?, ?, 5, '기존 후기', '기존 메모', '{}'::jsonb, ?, ?, ?, NULL, 2)
						""")) {
			statement.setObject(1, reviewId);
			statement.setObject(2, USER_ID);
			statement.setObject(3, RECIPE_ID);
			statement.setTimestamp(4, Timestamp.from(cookedAt));
			statement.setObject(5, clientSessionId);
			statement.setTimestamp(6, Timestamp.from(cookedAt));
			statement.executeUpdate();
		}
	}

	private void assertLegacyReviewPreserved(
			Connection connection,
			UUID reviewId,
			UUID clientSessionId,
			Instant cookedAt)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT
				  rating,
				  comment,
				  next_time_note,
				  structured_feedback,
				  created_at,
				  client_session_id,
				  cooked_at,
				  target_servings,
				  review_status,
				  cooking_result_schema_version,
				  cooking_result_payload,
				  cooking_result_fingerprint
				FROM post_cook_reviews
				WHERE id = ?
				""")) {
			statement.setObject(1, reviewId);
			try (ResultSet row = statement.executeQuery()) {
				assertThat(row.next()).isTrue();
				assertThat(row.getInt("rating")).isEqualTo(5);
				assertThat(row.getString("comment")).isEqualTo("기존 후기");
				assertThat(row.getString("next_time_note")).isEqualTo("기존 메모");
				assertThat(row.getString("structured_feedback")).isEqualTo("{}");
				assertThat(row.getTimestamp("created_at").toInstant())
						.isEqualTo(cookedAt);
				assertThat(row.getObject("client_session_id"))
						.isEqualTo(clientSessionId);
				assertThat(row.getTimestamp("cooked_at").toInstant())
						.isEqualTo(cookedAt);
				assertThat(row.getBigDecimal("target_servings"))
						.isEqualByComparingTo("2");
				assertThat(row.getString("review_status")).isEqualTo("FINALIZED");
				assertThat(row.getObject("cooking_result_schema_version")).isNull();
				assertThat(row.getString("cooking_result_payload")).isNull();
				assertThat(row.getString("cooking_result_fingerprint")).isNull();
			}
		}
	}

	private void assertValidStatesAccepted(Connection connection)
			throws SQLException {
		UUID pendingId = UUID.randomUUID();
		insertResult(
				connection,
				pendingId,
				"PENDING_REVIEW",
				(short) 1,
				"{\"recipeId\":\"" + RECIPE_ID + "\"}",
				VALID_FINGERPRINT);
		insertResult(
				connection,
				UUID.randomUUID(),
				"FINALIZED",
				(short) 1,
				"{\"recipeId\":\"" + RECIPE_ID + "\"}",
				VALID_FINGERPRINT);
		insertResult(
				connection,
				UUID.randomUUID(),
				"SKIPPED",
				(short) 1,
				"{\"recipeId\":\"" + RECIPE_ID + "\"}",
				VALID_FINGERPRINT);
		insertResult(
				connection,
				UUID.randomUUID(),
				"FINALIZED",
				null,
				null,
				null);

		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT review_status, cooking_result_payload
				FROM post_cook_reviews
				WHERE id = ?
				""")) {
			statement.setObject(1, pendingId);
			try (ResultSet row = statement.executeQuery()) {
				assertThat(row.next()).isTrue();
				assertThat(row.getString("review_status"))
						.isEqualTo("PENDING_REVIEW");
				assertThat(row.getString("cooking_result_payload"))
						.contains(RECIPE_ID.toString());
			}
		}
	}

	private void assertInvalidRowsRejected(Connection connection) {
		List<InvalidResult> invalidResults = List.of(
				new InvalidResult(
						"PENDING_REVIEW", null, null, null,
						"ck_reviews_pending_or_skipped_requires_result"),
				new InvalidResult(
						"SKIPPED", null, null, null,
						"ck_reviews_pending_or_skipped_requires_result"),
				new InvalidResult(
						"UNKNOWN", (short) 1, "{}", VALID_FINGERPRINT,
						"ck_reviews_review_status"),
				new InvalidResult(
						"FINALIZED", (short) 2, "{}", VALID_FINGERPRINT,
						"ck_reviews_cooking_result_bundle"),
				new InvalidResult(
						"FINALIZED", (short) 1, "[]", VALID_FINGERPRINT,
						"ck_reviews_cooking_result_bundle"),
				new InvalidResult(
						"FINALIZED", (short) 1, "{}", "A".repeat(64),
						"ck_reviews_cooking_result_bundle"),
				new InvalidResult(
						"FINALIZED", (short) 1, null, VALID_FINGERPRINT,
						"ck_reviews_cooking_result_bundle"));

		for (InvalidResult invalid : invalidResults) {
			assertThatThrownBy(() -> insertResult(
					connection,
					UUID.randomUUID(),
					invalid.status(),
					invalid.schemaVersion(),
					invalid.payload(),
					invalid.fingerprint()))
					.isInstanceOfSatisfying(SQLException.class, exception -> {
						assertThat(exception.getSQLState()).isEqualTo("23514");
						assertThat(exception.getMessage())
								.contains(invalid.constraintName());
					});
		}
	}

	private void assertNonFinalizedReviewDataRejected(Connection connection)
			throws SQLException {
		List<ReviewFields> invalidReviewFields = List.of(
				new ReviewFields(5, null, null),
				new ReviewFields(null, "완료 전 후기", null),
				new ReviewFields(null, null, "완료 전 메모"));

		for (String status : List.of("PENDING_REVIEW", "SKIPPED")) {
			for (ReviewFields reviewFields : invalidReviewFields) {
				assertThatThrownBy(() -> insertResultWithReview(
						connection,
						UUID.randomUUID(),
						status,
						(short) 1,
						"{}",
						VALID_FINGERPRINT,
						reviewFields.rating(),
						reviewFields.comment(),
						reviewFields.nextTimeNote()))
						.isInstanceOfSatisfying(SQLException.class, exception -> {
							assertThat(exception.getSQLState()).isEqualTo("23514");
							assertThat(exception.getMessage())
									.contains(
											"ck_reviews_non_finalized_review_data_empty");
						});
			}

			UUID id = UUID.randomUUID();
			insertResult(
					connection,
					id,
					status,
					(short) 1,
					"{}",
					VALID_FINGERPRINT);
			assertThatThrownBy(() -> updateStructuredFeedback(connection, id))
					.isInstanceOfSatisfying(SQLException.class, exception -> {
						assertThat(exception.getSQLState()).isEqualTo("23514");
						assertThat(exception.getMessage())
								.contains(
										"ck_reviews_non_finalized_review_data_empty");
					});
		}
	}

	private void updateStructuredFeedback(Connection connection, UUID id)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE post_cook_reviews
				SET structured_feedback = '{"summary":"완료 전 결과"}'::jsonb
				WHERE id = ?
				""")) {
			statement.setObject(1, id);
			statement.executeUpdate();
		}
	}

	private void insertResult(
			Connection connection,
			UUID id,
			String status,
			Short schemaVersion,
			String payload,
			String fingerprint) throws SQLException {
		insertResultWithReview(
				connection,
				id,
				status,
				schemaVersion,
				payload,
				fingerprint,
				null,
				null,
				null);
	}

	private void insertResultWithReview(
			Connection connection,
			UUID id,
			String status,
			Short schemaVersion,
			String payload,
			String fingerprint,
			Integer rating,
			String comment,
			String nextTimeNote) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO post_cook_reviews (
				  id,
				  user_id,
				  recipe_id,
				  rating,
				  comment,
				  next_time_note,
				  structured_feedback,
				  client_session_id,
				  cooked_at,
				  target_servings,
				  review_status,
				  cooking_result_schema_version,
				  cooking_result_payload,
				  cooking_result_fingerprint
				) VALUES (
				  ?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, NOW(), 2, ?, ?, ?::jsonb, ?
				)
				""")) {
			statement.setObject(1, id);
			statement.setObject(2, USER_ID);
			statement.setObject(3, RECIPE_ID);
			statement.setObject(4, rating);
			statement.setString(5, comment);
			statement.setString(6, nextTimeNote);
			statement.setObject(7, UUID.randomUUID());
			statement.setString(8, status);
			statement.setObject(9, schemaVersion);
			statement.setString(10, payload);
			statement.setString(11, fingerprint);
			statement.executeUpdate();
		}
	}

	private void assertConstraintValidationState(
			Connection connection, boolean expected) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT
				  COUNT(*) AS total_checks,
				  COUNT(*) FILTER (WHERE convalidated) AS validated_checks
				FROM pg_constraint
				WHERE conrelid = 'post_cook_reviews'::regclass
				  AND conname IN (
				    'ck_reviews_cooking_result_bundle',
				    'ck_reviews_review_status',
				    'ck_reviews_pending_or_skipped_requires_result',
				    'ck_reviews_non_finalized_review_data_empty'
				  )
				""");
					ResultSet constraints = statement.executeQuery()) {
			assertThat(constraints.next()).isTrue();
			assertThat(constraints.getInt("total_checks")).isEqualTo(4);
			assertThat(constraints.getInt("validated_checks"))
					.isEqualTo(expected ? 4 : 0);
		}
	}

	private void assertMigrationSucceeded(Connection connection, String version)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT success
				FROM flyway_schema_history
				WHERE version = ?
				""")) {
			statement.setString(1, version);
			try (ResultSet migration = statement.executeQuery()) {
				assertThat(migration.next()).isTrue();
				assertThat(migration.getBoolean("success")).isTrue();
			}
		}
	}

	private record InvalidResult(
			String status,
			Short schemaVersion,
			String payload,
			String fingerprint,
			String constraintName
	) {
	}

	private record ReviewFields(
			Integer rating,
			String comment,
			String nextTimeNote
	) {
	}
}
