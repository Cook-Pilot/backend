package com.cookpilot.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class H2MigrationUpgradeTest {
  @Test
  void upgradesAnExistingPreFlywayV1DatabaseAndIsRestartSafe() throws Exception {
    String url =
        "jdbc:h2:mem:upgrade_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.execute("RUNSCRIPT FROM 'classpath:db/h2/schema.sql'");
      statement.execute("RUNSCRIPT FROM 'classpath:db/h2/data.sql'");
      statement.execute(
          """
          INSERT INTO cook_sessions
            (id, user_id, recipe_id, status, current_step_index, started_at,
             setup_snapshot, created_at, updated_at)
          VALUES
            ('99999999-9999-9999-9999-999999999999',
             '00000000-0000-0000-0000-000000000001',
             '11111111-1111-1111-1111-111111111111',
             'paused', 2, CURRENT_TIMESTAMP, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
    }

    Flyway flyway =
        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/h2/migration")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(flyway.migrate().migrationsExecuted).isZero();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      try (ResultSet columns =
          statement.executeQuery(
              "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='cook_sessions' AND column_name='install_id'")) {
        columns.next();
        assertThat(columns.getInt(1)).isEqualTo(1);
      }
      try (ResultSet status =
          statement.executeQuery(
              "SELECT status FROM cook_sessions WHERE id='99999999-9999-9999-9999-999999999999'")) {
        status.next();
        assertThat(status.getString(1)).isEqualTo("aborted");
      }
      try (ResultSet metadata =
          statement.executeQuery(
              "SELECT start_confirmation_label, completion_cue FROM recipe_steps WHERE id='d1111111-1111-1111-1111-111111111111'")) {
        metadata.next();
        assertThat(metadata.getString(1)).contains("3분 시작");
        assertThat(metadata.getString(2)).contains("확인");
      }
    }
  }
}
