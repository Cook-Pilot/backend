package com.cookpilot.api;

import com.cookpilot.api.ApiModels.Ingredient;
import com.cookpilot.api.ApiModels.PersonalVersion;
import com.cookpilot.api.ApiModels.RecipeAdjustment;
import com.cookpilot.api.ApiModels.RecipeDetail;
import com.cookpilot.api.ApiModels.RecipeStep;
import com.cookpilot.api.ApiModels.RecipeSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class RecipeRepository {
  private final JdbcTemplate jdbc;
  private final PersonalizationEngine personalization;
  private final DatabaseJson databaseJson;

  RecipeRepository(
      JdbcTemplate jdbc,
      PersonalizationEngine personalization,
      DatabaseJson databaseJson) {
    this.jdbc = jdbc;
    this.personalization = personalization;
    this.databaseJson = databaseJson;
  }

  List<RecipeSummary> findSummaries(UUID installId) {
    return jdbc.query(
        """
        SELECT r.id, r.title, r.description, r.image_key, r.total_minutes, r.difficulty,
               pv.summary AS personal_summary
        FROM recipes r
        LEFT JOIN personal_recipe_defaults pd
          ON pd.recipe_id = r.id AND pd.install_id = ?
        LEFT JOIN personal_recipe_versions pv ON pv.id = pd.default_version_id
        WHERE r.status = 'active'
        ORDER BY r.sort_order, r.created_at
        """,
        (resultSet, rowNumber) ->
            new RecipeSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("image_key"),
                resultSet.getInt("total_minutes"),
                resultSet.getString("difficulty"),
                resultSet.getString("personal_summary")),
        installId);
  }

  RecipeDetail findDetail(UUID recipeId, UUID installId) {
    RecipeBase recipe = findBase(recipeId);
    List<Ingredient> ingredients = findIngredients(recipeId);
    List<RecipeStep> steps = findSteps(recipeId);
    DefaultPointer pointer = findPointer(installId, recipeId);
    PersonalVersion version =
        pointer.defaultVersionId() == null
            ? null
            : findVersion(pointer.defaultVersionId(), installId, recipeId).orElse(null);
    List<Ingredient> personalizedIngredients =
        version == null ? ingredients : personalization.applyIngredients(ingredients, version.adjustments());
    List<RecipeStep> personalizedSteps =
        version == null ? steps : personalization.applySteps(steps, version.adjustments());
    return new RecipeDetail(
        recipe.id(),
        recipe.title(),
        recipe.description(),
        recipe.imageKey(),
        recipe.totalMinutes(),
        recipe.difficulty(),
        recipe.servings(),
        recipe.contentRevision(),
        ingredients,
        steps,
        personalizedIngredients,
        personalizedSteps,
        version,
        pointer.revision());
  }

  RecipeBase findBase(UUID recipeId) {
    List<RecipeBase> rows =
        jdbc.query(
            """
            SELECT id, title, description, image_key, total_minutes, difficulty,
                   base_servings, content_revision
            FROM recipes
            WHERE id = ? AND status = 'active'
            """,
            (resultSet, rowNumber) ->
                new RecipeBase(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("title"),
                    resultSet.getString("description"),
                    resultSet.getString("image_key"),
                    resultSet.getInt("total_minutes"),
                    resultSet.getString("difficulty"),
                    resultSet.getDouble("base_servings"),
                    resultSet.getInt("content_revision")),
            recipeId);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "레시피를 찾을 수 없습니다.");
    }
    return rows.getFirst();
  }

  List<Ingredient> findIngredients(UUID recipeId) {
    return jdbc.query(
        """
        SELECT id, name, amount, unit, is_required, sort_order,
               personalization_role, personalization_locked
        FROM recipe_ingredients
        WHERE recipe_id = ?
        ORDER BY sort_order, name
        """,
        (resultSet, rowNumber) ->
            new Ingredient(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getDouble("amount"),
                resultSet.getString("unit"),
                resultSet.getBoolean("is_required"),
                resultSet.getInt("sort_order"),
                resultSet.getString("personalization_role"),
                resultSet.getBoolean("personalization_locked")),
        recipeId);
  }

  List<RecipeStep> findSteps(UUID recipeId) {
    return jdbc.query(
        """
        SELECT id, step_index, instruction, timer_seconds, caution_note,
               timer_adjustable, timer_min_seconds, timer_max_seconds, safety_tier,
               start_confirmation_label, completion_cue
        FROM recipe_steps
        WHERE recipe_id = ?
        ORDER BY step_index
        """,
        (resultSet, rowNumber) ->
            new RecipeStep(
                resultSet.getObject("id", UUID.class),
                resultSet.getInt("step_index"),
                resultSet.getString("instruction"),
                resultSet.getObject("timer_seconds", Integer.class),
                resultSet.getString("caution_note"),
                resultSet.getBoolean("timer_adjustable"),
                resultSet.getObject("timer_min_seconds", Integer.class),
                resultSet.getObject("timer_max_seconds", Integer.class),
                resultSet.getString("safety_tier"),
                resultSet.getString("start_confirmation_label"),
                resultSet.getString("completion_cue")),
        recipeId);
  }

  Optional<PersonalVersion> findVersion(UUID id, UUID installId, UUID recipeId) {
    return jdbc.query(
            """
            SELECT id, version_number, title, summary, parent_version_id,
                   base_recipe_revision, created_at
            FROM personal_recipe_versions
            WHERE id = ? AND install_id = ? AND recipe_id = ?
            """,
            this::mapVersion,
            id,
            installId,
            recipeId)
        .stream()
        .findFirst();
  }

  PersonalVersion findVersionRequired(UUID id, UUID installId, UUID recipeId) {
    return findVersion(id, installId, recipeId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "개인 레시피 버전을 찾을 수 없습니다."));
  }

  DefaultPointer findPointer(UUID installId, UUID recipeId) {
    return jdbc.query(
            """
            SELECT default_version_id, pointer_revision
            FROM personal_recipe_defaults
            WHERE install_id = ? AND recipe_id = ?
            """,
            (resultSet, rowNumber) ->
                new DefaultPointer(
                    resultSet.getObject("default_version_id", UUID.class),
                    resultSet.getInt("pointer_revision")),
            installId,
            recipeId)
        .stream()
        .findFirst()
        .orElse(new DefaultPointer(null, 0));
  }

  void ensurePointer(UUID installId, UUID recipeId) {
    if (!databaseJson.isPostgres()) {
      Integer count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM personal_recipe_defaults WHERE install_id = ? AND recipe_id = ?",
              Integer.class,
              installId,
              recipeId);
      if (count != null && count > 0) {
        return;
      }
      jdbc.update(
          "INSERT INTO personal_recipe_defaults (install_id, recipe_id, default_version_id, pointer_revision, updated_at) VALUES (?, ?, NULL, 0, ?)",
          installId,
          recipeId,
          Timestamp.from(Instant.now()));
      return;
    }
    jdbc.update(
        "INSERT INTO personal_recipe_defaults (install_id, recipe_id, default_version_id, pointer_revision, updated_at) VALUES (?, ?, NULL, 0, ?) ON CONFLICT (install_id, recipe_id) DO NOTHING",
        installId,
        recipeId,
        Timestamp.from(Instant.now()));
  }

  DefaultPointer lockPointer(UUID installId, UUID recipeId) {
    ensurePointer(installId, recipeId);
    return jdbc.query(
            """
            SELECT default_version_id, pointer_revision
            FROM personal_recipe_defaults
            WHERE install_id = ? AND recipe_id = ?
            FOR UPDATE
            """,
            (resultSet, rowNumber) ->
                new DefaultPointer(
                    resultSet.getObject("default_version_id", UUID.class),
                    resultSet.getInt("pointer_revision")),
            installId,
            recipeId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Default pointer could not be locked"));
  }

  int nextVersionNumber(UUID installId, UUID recipeId) {
    Integer value =
        jdbc.queryForObject(
            """
            SELECT COALESCE(MAX(version_number), 0) + 1
            FROM personal_recipe_versions
            WHERE install_id = ? AND recipe_id = ?
            """,
            Integer.class,
            installId,
            recipeId);
    return value == null ? 1 : value;
  }

  PersonalVersion insertVersion(
      UUID id,
      InstallPrincipal principal,
      UUID recipeId,
      int versionNumber,
      String title,
      String summary,
      UUID parentVersionId,
      UUID sourceProposalId,
      int baseRecipeRevision) {
    Instant now = Instant.now();
    jdbc.update(
        """
        INSERT INTO personal_recipe_versions
          (id, user_id, install_id, recipe_id, version_number, title, summary,
           adjustment_payload, source_session_id, is_default, parent_version_id,
           source_proposal_id, base_recipe_revision, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, '{}', NULL, FALSE, ?, ?, ?, ?, ?)
        """,
        id,
        principal.userId(),
        principal.installId(),
        recipeId,
        versionNumber,
        title,
        summary,
        parentVersionId,
        sourceProposalId,
        baseRecipeRevision,
        Timestamp.from(now),
        Timestamp.from(now));
    return findVersionRequired(id, principal.installId(), recipeId);
  }

  void copyVersionItems(UUID parentVersionId, UUID newVersionId) {
    if (parentVersionId == null) {
      return;
    }
    List<VersionItemRow> items = findVersionItems(parentVersionId);
    for (VersionItemRow item : items) {
      insertVersionItem(newVersionId, item);
    }
  }

  void replaceVersionItem(UUID versionId, ProposalItemRow item) {
    jdbc.update(
        "DELETE FROM personal_recipe_version_items WHERE version_id = ? AND item_key = ?",
        versionId,
        item.itemKey());
    insertVersionItem(
        versionId,
        new VersionItemRow(
            item.itemKey(),
            item.itemType(),
            item.label(),
            item.ingredientId(),
            item.stepId(),
            item.proposedAmount(),
            item.proposedSeconds()));
  }

  void updatePointer(UUID installId, UUID recipeId, UUID versionId, int expectedRevision) {
    int changed =
        jdbc.update(
            """
            UPDATE personal_recipe_defaults
            SET default_version_id = ?, pointer_revision = pointer_revision + 1, updated_at = ?
            WHERE install_id = ? AND recipe_id = ? AND pointer_revision = ?
            """,
            versionId,
            Timestamp.from(Instant.now()),
            installId,
            recipeId,
            expectedRevision);
    if (changed != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "개인 레시피가 다른 요청에서 먼저 변경되었습니다.");
    }
  }

  private void insertVersionItem(UUID versionId, VersionItemRow item) {
    jdbc.update(
        """
        INSERT INTO personal_recipe_version_items
          (id, version_id, item_key, item_type, label, ingredient_id, step_id,
           effective_amount, effective_seconds)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        versionId,
        item.itemKey(),
        item.itemType(),
        item.label(),
        item.ingredientId(),
        item.stepId(),
        item.effectiveAmount(),
        item.effectiveSeconds());
  }

  private PersonalVersion mapVersion(ResultSet resultSet, int rowNumber) throws SQLException {
    UUID versionId = resultSet.getObject("id", UUID.class);
    List<RecipeAdjustment> adjustments =
        findVersionItems(versionId).stream()
            .map(
                item ->
                    new RecipeAdjustment(
                        item.itemKey(),
                        item.itemType(),
                        item.label(),
                        item.ingredientId(),
                        item.stepId(),
                        item.effectiveAmount(),
                        item.effectiveSeconds()))
            .toList();
    return new PersonalVersion(
        versionId,
        resultSet.getInt("version_number"),
        resultSet.getString("title"),
        resultSet.getString("summary"),
        resultSet.getObject("parent_version_id", UUID.class),
        resultSet.getInt("base_recipe_revision"),
        adjustments,
        resultSet.getTimestamp("created_at").toInstant());
  }

  private List<VersionItemRow> findVersionItems(UUID versionId) {
    return jdbc.query(
        """
        SELECT item_key, item_type, label, ingredient_id, step_id,
               effective_amount, effective_seconds
        FROM personal_recipe_version_items
        WHERE version_id = ?
        ORDER BY item_key
        """,
        (resultSet, rowNumber) ->
            new VersionItemRow(
                resultSet.getString("item_key"),
                resultSet.getString("item_type"),
                resultSet.getString("label"),
                resultSet.getObject("ingredient_id", UUID.class),
                resultSet.getObject("step_id", UUID.class),
                resultSet.getObject("effective_amount", Double.class),
                resultSet.getObject("effective_seconds", Integer.class)),
        versionId);
  }
}

record RecipeBase(
    UUID id,
    String title,
    String description,
    String imageKey,
    int totalMinutes,
    String difficulty,
    double servings,
    int contentRevision) {}

record DefaultPointer(UUID defaultVersionId, int revision) {}

record VersionItemRow(
    String itemKey,
    String itemType,
    String label,
    UUID ingredientId,
    UUID stepId,
    Double effectiveAmount,
    Integer effectiveSeconds) {}
