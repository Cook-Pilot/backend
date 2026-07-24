package com.cookpilot.api;

import com.cookpilot.api.ApiModels.AiFeedbackResponse;
import com.cookpilot.api.ApiModels.CookSessionResponse;
import com.cookpilot.api.ApiModels.CookSessionStateResponse;
import com.cookpilot.api.ApiModels.CreateCookSessionRequest;
import com.cookpilot.api.ApiModels.Ingredient;
import com.cookpilot.api.ApiModels.PersonalVersion;
import com.cookpilot.api.ApiModels.RecordEventRequest;
import com.cookpilot.api.ApiModels.RecipeDetail;
import com.cookpilot.api.ApiModels.RecipeStep;
import com.cookpilot.api.ApiModels.VoiceTranscriptRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
class CookSessionRepository {
  private final JdbcTemplate jdbc;
  private final RecipeRepository recipes;
  private final PersonalizationEngine personalization;
  private final DatabaseJson databaseJson;
  private final ObjectMapper objectMapper;

  CookSessionRepository(
      JdbcTemplate jdbc,
      RecipeRepository recipes,
      PersonalizationEngine personalization,
      DatabaseJson databaseJson,
      ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.recipes = recipes;
    this.personalization = personalization;
    this.databaseJson = databaseJson;
    this.objectMapper = objectMapper;
  }

  @Transactional
  CookSessionResponse create(InstallPrincipal principal, CreateCookSessionRequest request) {
    RecipeDetail detail = recipes.findDetail(request.recipeId(), principal.installId());
    PersonalVersion selectedVersion = null;
    if (request.personalVersionId() != null) {
      selectedVersion =
          recipes
              .findVersion(request.personalVersionId(), principal.installId(), request.recipeId())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "선택한 개인 레시피 버전을 찾을 수 없습니다."));
    }
    List<Ingredient> ingredients =
        selectedVersion == null
            ? detail.baseIngredients()
            : personalization.applyIngredients(detail.baseIngredients(), selectedVersion.adjustments());
    List<RecipeStep> steps =
        selectedVersion == null
            ? detail.baseSteps()
            : personalization.applySteps(detail.baseSteps(), selectedVersion.adjustments());
    validateRecipe(ingredients, steps);

    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("recipeId", detail.id());
    snapshot.put("recipeTitle", detail.title());
    snapshot.put("recipeRevision", detail.contentRevision());
    snapshot.put("personalVersionId", selectedVersion == null ? null : selectedVersion.id());
    snapshot.put("personalized", selectedVersion != null);
    snapshot.put("ingredients", ingredients);
    snapshot.put("steps", steps);
    try {
      jdbc.update(
          """
          INSERT INTO cook_sessions
            (id, user_id, install_id, active_install_id, recipe_id, personal_version_id,
             status, current_step_index, revision, review_state, started_at,
             setup_snapshot, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, 'cooking', 0, 0, 'not_eligible', ?, ?, ?, ?)
          """,
          id,
          principal.userId(),
          principal.installId(),
          principal.installId(),
          request.recipeId(),
          selectedVersion == null ? null : selectedVersion.id(),
          Timestamp.from(now),
          databaseJson.value(snapshot),
          Timestamp.from(now),
          Timestamp.from(now));
    } catch (DuplicateKeyException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 진행 중인 조리가 있습니다.");
    }
    return new CookSessionResponse(
        id,
        "cooking",
        0,
        selectedVersion != null,
        request.recipeId(),
        selectedVersion == null ? null : selectedVersion.id(),
        ingredients,
        steps);
  }

  Optional<CookSessionStateResponse> findActive(InstallPrincipal principal) {
    return jdbc.query(
            """
            SELECT id, status, revision, review_state, recipe_id, personal_version_id,
                   setup_snapshot
            FROM cook_sessions
            WHERE active_install_id = ? AND status = 'cooking'
            """,
            this::mapState,
            principal.installId())
        .stream()
        .findFirst();
  }

  Optional<CookSessionStateResponse> findReviewable(InstallPrincipal principal) {
    return jdbc.query(
            """
            SELECT id, status, revision, review_state, recipe_id, personal_version_id,
                   setup_snapshot
            FROM cook_sessions
            WHERE install_id = ? AND status = 'completed' AND review_state = 'open'
            ORDER BY completed_at DESC, created_at DESC
            LIMIT 1
            """,
            this::mapState,
            principal.installId())
        .stream()
        .findFirst();
  }

  CookSessionStateResponse findState(InstallPrincipal principal, UUID sessionId) {
    return findSessionRow(principal, sessionId).state();
  }

  void recordEvent(InstallPrincipal principal, UUID sessionId, RecordEventRequest request) {
    ensureOwned(principal, sessionId);
    UUID clientEventId = request.clientEventId() == null ? UUID.randomUUID() : request.clientEventId();
    if (!databaseJson.isPostgres()) {
      Integer count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM cook_session_events WHERE cook_session_id = ? AND client_event_id = ?",
              Integer.class,
              sessionId,
              clientEventId);
      if (count != null && count > 0) {
        return;
      }
    }
    String sql =
        databaseJson.isPostgres()
            ? "INSERT INTO cook_session_events (id, cook_session_id, client_event_id, event_type, step_index, source, payload, created_at) VALUES (?, ?, ?, ?, NULL, ?, ?, ?) ON CONFLICT (cook_session_id, client_event_id) DO NOTHING"
            : "INSERT INTO cook_session_events (id, cook_session_id, client_event_id, event_type, step_index, source, payload, created_at) VALUES (?, ?, ?, ?, NULL, ?, ?, ?)";
    jdbc.update(
        sql,
        UUID.randomUUID(),
        sessionId,
        clientEventId,
        request.eventType(),
        request.source(),
        databaseJson.value(safeEventPayload(request)),
        Timestamp.from(Instant.now()));
  }

  void recordTranscriptIntent(
      InstallPrincipal principal, UUID sessionId, VoiceTranscriptRequest request) {
    ensureActive(principal, sessionId);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("intent", request.routedIntent() == null ? "unknown" : request.routedIntent());
    payload.put("confidence", request.confidence());
    payload.put("stepId", request.stepId());
    // Intentionally do not persist request.transcript().
    jdbc.update(
        """
        INSERT INTO cook_session_events
          (id, cook_session_id, client_event_id, event_type, step_index, source, payload, created_at)
        VALUES (?, ?, ?, 'voice_intent', NULL, 'voice', ?, ?)
        """,
        UUID.randomUUID(),
        sessionId,
        UUID.randomUUID(),
        databaseJson.value(payload),
        Timestamp.from(Instant.now()));
  }

  SessionContext findContext(
      InstallPrincipal principal,
      UUID sessionId,
      int requestedStepIndex,
      Integer remainingSeconds) {
    ensureActive(principal, sessionId);
    List<SessionSnapshotRow> rows =
        jdbc.query(
            """
            SELECT r.title AS recipe_title, cs.setup_snapshot
            FROM cook_sessions cs
            JOIN recipes r ON r.id = cs.recipe_id
            WHERE cs.id = ? AND cs.install_id = ?
            """,
            (resultSet, rowNumber) ->
                new SessionSnapshotRow(
                    resultSet.getString("recipe_title"),
                    resultSet.getString("setup_snapshot")),
            sessionId,
            principal.installId());
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조리 세션을 찾을 수 없습니다.");
    }
    SessionSnapshotRow row = rows.getFirst();
    JsonNode snapshot = databaseJson.parse(row.snapshot());
    List<RecipeStep> steps =
        objectMapper.convertValue(snapshot.path("steps"), new TypeReference<>() {});
    if (requestedStepIndex < 0 || requestedStepIndex >= steps.size()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "현재 조리 단계가 유효하지 않습니다.");
    }
    RecipeStep step = steps.get(requestedStepIndex);
    return new SessionContext(
        sessionId,
        row.recipeTitle(),
        step.id(),
        requestedStepIndex,
        step.instruction(),
        step.timerSeconds(),
        remainingSeconds);
  }

  void recordAiInteraction(
      UUID sessionId,
      SessionContext context,
      String model,
      AiFeedbackResponse response) {
    Map<String, Object> contextPayload = new LinkedHashMap<>();
    contextPayload.put("recipeName", context.recipeTitle());
    contextPayload.put("stepId", context.stepId());
    contextPayload.put("stepIndex", context.stepIndex());
    contextPayload.put("targetSeconds", context.targetSeconds());
    contextPayload.put("remainingSeconds", context.remainingSeconds());
    Object action = response.suggestedAction() == null ? Map.of() : response.suggestedAction();
    jdbc.update(
        """
        INSERT INTO ai_interactions
          (id, cook_session_id, step_index, model, user_message, context_payload,
           response_text, action_payload, created_at)
        VALUES (?, ?, ?, ?, '[redacted]', ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        sessionId,
        context.stepIndex(),
        model,
        databaseJson.value(contextPayload),
        response.screenText(),
        databaseJson.value(action),
        Timestamp.from(Instant.now()));
  }

  @Transactional
  CookSessionStateResponse complete(InstallPrincipal principal, UUID sessionId) {
    Instant now = Instant.now();
    int changed =
        jdbc.update(
            """
            UPDATE cook_sessions
            SET status = 'completed', active_install_id = NULL, review_state = 'open',
                revision = revision + 1, completed_at = ?, updated_at = ?
            WHERE id = ? AND install_id = ? AND status = 'cooking'
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            sessionId,
            principal.installId());
    SessionOwnedRow row = findSessionRow(principal, sessionId);
    if (changed == 0 && !"completed".equals(row.state().status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "종료된 조리 세션은 완료할 수 없습니다.");
    }
    return row.state();
  }

  @Transactional
  CookSessionStateResponse abort(InstallPrincipal principal, UUID sessionId) {
    Instant now = Instant.now();
    int changed =
        jdbc.update(
            """
            UPDATE cook_sessions
            SET status = 'aborted', active_install_id = NULL, review_state = 'not_eligible',
                revision = revision + 1, aborted_at = ?, updated_at = ?
            WHERE id = ? AND install_id = ? AND status = 'cooking'
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            sessionId,
            principal.installId());
    SessionOwnedRow row = findSessionRow(principal, sessionId);
    if (changed == 0 && !"aborted".equals(row.state().status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "완료된 조리 세션은 중단할 수 없습니다.");
    }
    return row.state();
  }

  @Transactional
  CookSessionStateResponse skipReview(InstallPrincipal principal, UUID sessionId) {
    int changed =
        jdbc.update(
            """
            UPDATE cook_sessions
            SET review_state = 'skipped', revision = revision + 1, updated_at = ?
            WHERE id = ? AND install_id = ? AND status = 'completed' AND review_state = 'open'
            """,
            Timestamp.from(Instant.now()),
            sessionId,
            principal.installId());
    SessionOwnedRow row = findSessionRow(principal, sessionId);
    if (changed == 0 && !"skipped".equals(row.state().reviewState())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "후기를 건너뛸 수 없는 세션 상태입니다.");
    }
    return row.state();
  }

  ReviewableSession claimForReview(InstallPrincipal principal, UUID sessionId) {
    int changed =
        jdbc.update(
            """
            UPDATE cook_sessions
            SET review_state = 'submitted', revision = revision + 1, updated_at = ?
            WHERE id = ? AND install_id = ? AND status = 'completed' AND review_state = 'open'
            """,
            Timestamp.from(Instant.now()),
            sessionId,
            principal.installId());
    SessionOwnedRow row = findSessionRow(principal, sessionId);
    if (changed != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "완료된 조리에는 후기를 한 번만 남길 수 있습니다.");
    }
    return new ReviewableSession(
        sessionId,
        row.state().recipeId(),
        row.state().personalVersionId());
  }

  void ensureActive(InstallPrincipal principal, UUID sessionId) {
    SessionOwnedRow row = findSessionRow(principal, sessionId);
    if (!"cooking".equals(row.state().status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 종료된 조리 세션입니다.");
    }
  }

  private void ensureOwned(InstallPrincipal principal, UUID sessionId) {
    findSessionRow(principal, sessionId);
  }

  private SessionOwnedRow findSessionRow(InstallPrincipal principal, UUID sessionId) {
    return jdbc.query(
            """
            SELECT id, status, revision, review_state, recipe_id, personal_version_id,
                   setup_snapshot
            FROM cook_sessions
            WHERE id = ? AND install_id = ?
            """,
            (resultSet, rowNumber) ->
                new SessionOwnedRow(mapState(resultSet, rowNumber)),
            sessionId,
            principal.installId())
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "조리 세션을 찾을 수 없습니다."));
  }

  private CookSessionStateResponse mapState(java.sql.ResultSet resultSet, int rowNumber)
      throws java.sql.SQLException {
    JsonNode snapshot = databaseJson.parse(resultSet.getString("setup_snapshot"));
    List<Ingredient> ingredients =
        snapshot.path("ingredients").isArray()
            ? objectMapper.convertValue(snapshot.path("ingredients"), new TypeReference<>() {})
            : List.of();
    List<RecipeStep> steps =
        snapshot.path("steps").isArray()
            ? objectMapper.convertValue(snapshot.path("steps"), new TypeReference<>() {})
            : List.of();
    boolean personalized =
        snapshot.hasNonNull("personalized")
            ? snapshot.path("personalized").asBoolean()
            : snapshot.hasNonNull("personalVersionId");
    return new CookSessionStateResponse(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("status"),
        resultSet.getInt("revision"),
        resultSet.getString("review_state"),
        resultSet.getObject("recipe_id", UUID.class),
        resultSet.getObject("personal_version_id", UUID.class),
        personalized,
        List.copyOf(ingredients),
        List.copyOf(steps));
  }

  private Map<String, Object> safeEventPayload(RecordEventRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("stepId", request.stepId());
    if (request.payload() != null) {
      request.payload().forEach(
          (key, value) -> {
            if (!List.of("transcript", "comment", "nextTimeNote", "userSpeech").contains(key)) {
              payload.put(key, value);
            }
          });
    }
    return payload;
  }

  private void validateRecipe(List<Ingredient> ingredients, List<RecipeStep> steps) {
    if (steps.isEmpty()
        || steps.stream().anyMatch(step -> step.id() == null || step.instruction() == null || step.instruction().isBlank())) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "레시피 단계가 유효하지 않습니다.");
    }
    for (int index = 0; index < steps.size(); index++) {
      if (steps.get(index).stepIndex() != index) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "레시피 단계 순서가 유효하지 않습니다.");
      }
      Integer seconds = steps.get(index).timerSeconds();
      if (seconds != null && (seconds < 1 || seconds > 3600)) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "레시피 타이머가 유효하지 않습니다.");
      }
    }
    if (ingredients.stream()
        .anyMatch(item -> item.id() == null || item.name().isBlank() || item.unit().isBlank() || item.amount() <= 0)) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "레시피 재료가 유효하지 않습니다.");
    }
  }
}

record SessionContext(
    UUID sessionId,
    String recipeTitle,
    UUID stepId,
    int stepIndex,
    String instruction,
    Integer targetSeconds,
    Integer remainingSeconds) {}

record SessionSnapshotRow(String recipeTitle, String snapshot) {}

record SessionOwnedRow(ApiModels.CookSessionStateResponse state) {}

record ReviewableSession(UUID sessionId, UUID recipeId, UUID personalVersionId) {}
