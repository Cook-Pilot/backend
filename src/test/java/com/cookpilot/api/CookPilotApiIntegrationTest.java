package com.cookpilot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:cookpilot_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.flyway.enabled=true",
      "spring.flyway.locations=classpath:db/h2/migration",
      "spring.sql.init.mode=never",
      "cookpilot.ai.gemini-api-key="
    })
@AutoConfigureMockMvc
class CookPilotApiIntegrationTest {
  private static final String RAMEN_ID = "11111111-1111-1111-1111-111111111111";
  private static final String RAMEN_TIMED_STEP_ID = "d1131111-1111-1111-1111-111111111111";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbc;

  @Test
  void completesApprovalCumulativeVersionAndRollbackLoop() throws Exception {
    Credentials credentials = bootstrap();

    mockMvc
        .perform(get("/api/recipes").header("Authorization", credentials.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
    mockMvc
        .perform(
            get("/api/recipes/{id}", RAMEN_ID)
                .header("Authorization", credentials.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseSteps[0].id").value("d1111111-1111-1111-1111-111111111111"))
        .andExpect(jsonPath("$.baseSteps[0].startConfirmationLabel").value("물이 끓기 시작했어요 · 3분 시작"))
        .andExpect(jsonPath("$.baseSteps[0].completionCue").value("물이 힘있게 끓는지 확인"));

    UUID createKey = UUID.randomUUID();
    MvcResult firstCreate = createSession(credentials, createKey, null, 201);
    JsonNode firstSession = json(firstCreate);
    String firstSessionId = firstSession.path("id").asText();

    MvcResult replay = createSession(credentials, createKey, null, 201);
    assertThat(json(replay).path("id").asText()).isEqualTo(firstSessionId);

    mockMvc
        .perform(
            post("/api/cook-sessions")
                .header("Authorization", credentials.authorization())
                .header("Idempotency-Key", createKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonString(Map.of("recipeId", "22222222-2222-2222-2222-222222222222"))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            post("/api/cook-sessions")
                .header("Authorization", credentials.authorization())
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonString(Map.of("recipeId", RAMEN_ID))))
        .andExpect(status().isConflict());

    Integer beforeStep =
        jdbc.queryForObject(
            "SELECT current_step_index FROM cook_sessions WHERE id = ?",
            Integer.class,
            UUID.fromString(firstSessionId));
    mockMvc
        .perform(
            post("/api/cook-sessions/{id}/events", firstSessionId)
                .header("Authorization", credentials.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonString(
                        Map.of(
                            "clientEventId", UUID.randomUUID(),
                            "eventType", "step_viewed",
                            "stepId", RAMEN_TIMED_STEP_ID,
                            "source", "tap",
                            "payload", Map.of("displayIndex", 2)))))
        .andExpect(status().isAccepted());
    Integer afterStep =
        jdbc.queryForObject(
            "SELECT current_step_index FROM cook_sessions WHERE id = ?",
            Integer.class,
            UUID.fromString(firstSessionId));
    assertThat(afterStep).isEqualTo(beforeStep);

    mockMvc
        .perform(
            post("/api/ai/feedback")
                .header("Authorization", credentials.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonString(
                        Map.of(
                            "cookSessionId", firstSessionId,
                            "userSpeech", "닭이 안 익은 것 같아",
                            "stepIndex", 0,
                            "remainingSeconds", 20))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suggestedAction").doesNotExist())
        .andExpect(jsonPath("$.screenText").value(org.hamcrest.Matchers.containsString("중심 온도")));

    complete(credentials, firstSessionId, UUID.randomUUID());
    MvcResult firstReview =
        review(
            credentials,
            firstSessionId,
            List.of(
                Map.of("tag", "TOO_SALTY"),
                Map.of("tag", "TOO_SOFT", "stepId", RAMEN_TIMED_STEP_ID)));
    JsonNode firstReviewJson = json(firstReview);
    assertThat(firstReviewJson.path("result").asText()).isEqualTo("PROPOSAL_READY");
    // The salty ingredient is eligible; the caution step remains evidence-only.
    assertThat(firstReviewJson.at("/proposal/items").size()).isEqualTo(1);

    mockMvc
        .perform(
            get("/api/recipes/{id}", RAMEN_ID)
                .header("Authorization", credentials.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.personalVersion").doesNotExist());

    String firstProposalId = firstReviewJson.at("/proposal/id").asText();
    List<String> firstItemIds = new java.util.ArrayList<>();
    firstReviewJson.at("/proposal/items").forEach(item -> firstItemIds.add(item.path("id").asText()));
    MvcResult firstApproval = approve(credentials, firstProposalId, firstItemIds, 0);
    JsonNode firstApprovalJson = json(firstApproval);
    String firstVersionId = firstApprovalJson.at("/version/id").asText();
    assertThat(firstApprovalJson.path("pointerRevision").asInt()).isEqualTo(1);

    mockMvc
        .perform(
            get("/api/recipes/{id}", RAMEN_ID)
                .header("Authorization", credentials.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.personalizedIngredients[2].amount").value(0.9))
        .andExpect(jsonPath("$.personalizedSteps[2].timerSeconds").value(180));

    String secondSessionId =
        json(createSession(credentials, UUID.randomUUID(), firstVersionId, 201)).path("id").asText();
    complete(credentials, secondSessionId, UUID.randomUUID());
    JsonNode secondReview =
        json(
            review(
                credentials,
                secondSessionId,
                List.of(Map.of("tag", "TOO_SALTY"))));
    String secondProposalId = secondReview.at("/proposal/id").asText();
    String secondItemId = secondReview.at("/proposal/items/0/id").asText();
    JsonNode secondApproval =
        json(approve(credentials, secondProposalId, List.of(secondItemId), 1));
    String secondVersionId = secondApproval.at("/version/id").asText();

    mockMvc
        .perform(
            get("/api/recipes/{id}", RAMEN_ID)
                .header("Authorization", credentials.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.personalizedIngredients[2].amount").value(0.81))
        .andExpect(jsonPath("$.personalizedSteps[2].timerSeconds").value(180));

    mockMvc
        .perform(
            post("/api/personal-recipes/{id}/default-version/rollback", RAMEN_ID)
                .header("Authorization", credentials.authorization())
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonString(
                        Map.of(
                            "expectedCurrentVersionId", secondVersionId,
                            "expectedPointerRevision", 2))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultVersion.id").value(firstVersionId))
        .andExpect(jsonPath("$.pointerRevision").value(3));

    mockMvc
        .perform(
            get("/api/recipes/{id}", RAMEN_ID)
                .header("Authorization", credentials.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.personalizedIngredients[2].amount").value(0.9))
        .andExpect(jsonPath("$.personalizedSteps[2].timerSeconds").value(180));

    Integer versionCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM personal_recipe_versions WHERE install_id = ?",
            Integer.class,
            credentials.installId());
    assertThat(versionCount).isEqualTo(2);
  }

  @Test
  void enforcesOwnershipNoChangeReviewAndTerminalTransitions() throws Exception {
    Credentials owner = bootstrap();
    Credentials other = bootstrap();
    String sessionId = json(createSession(owner, UUID.randomUUID(), null, 201)).path("id").asText();

    mockMvc
        .perform(
            get("/api/cook-sessions/{id}", sessionId)
                .header("Authorization", other.authorization()))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/recipes")).andExpect(status().isUnauthorized());

    UUID abortKey = UUID.randomUUID();
    MvcResult aborted =
        mockMvc
            .perform(
                post("/api/cook-sessions/{id}/abort", sessionId)
                    .header("Authorization", owner.authorization())
                    .header("Idempotency-Key", abortKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("aborted"))
            .andReturn();
    MvcResult replay =
        mockMvc
            .perform(
                post("/api/cook-sessions/{id}/abort", sessionId)
                    .header("Authorization", owner.authorization())
                    .header("Idempotency-Key", abortKey))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(replay.getResponse().getContentAsString())
        .isEqualTo(aborted.getResponse().getContentAsString());

    mockMvc
        .perform(
            post("/api/cook-sessions/{id}/complete", sessionId)
                .header("Authorization", owner.authorization())
                .header("Idempotency-Key", UUID.randomUUID()))
        .andExpect(status().isConflict());

    String completedId = json(createSession(owner, UUID.randomUUID(), null, 201)).path("id").asText();
    complete(owner, completedId, UUID.randomUUID());
    mockMvc
        .perform(
            get("/api/cook-sessions/reviewable")
                .header("Authorization", owner.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(completedId));
    MvcResult noChange =
        review(owner, completedId, List.of(Map.of("tag", "JUST_RIGHT")));
    assertThat(json(noChange).path("result").asText()).isEqualTo("NO_CHANGE");
    mockMvc
        .perform(
            get("/api/cook-sessions/reviewable")
                .header("Authorization", owner.authorization()))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM personal_recipe_proposals p JOIN post_cook_reviews r ON r.id=p.review_id WHERE r.cook_session_id=?",
                Integer.class,
                UUID.fromString(completedId)))
        .isZero();

    mockMvc
        .perform(
            post("/api/cook-sessions/{id}/review", completedId)
                .header("Authorization", owner.authorization())
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonString(Map.of("rating", 4, "signals", List.of(Map.of("tag", "JUST_RIGHT"))))))
        .andExpect(status().isConflict());
  }

  @Test
  void returnsOwnedActiveSnapshotAndKeepsItOnTerminalState() throws Exception {
    Credentials owner = bootstrap();
    Credentials other = bootstrap();

    mockMvc
        .perform(get("/api/cook-sessions/active").header("Authorization", owner.authorization()))
        .andExpect(status().isNoContent());

    JsonNode created = json(createSession(owner, UUID.randomUUID(), null, 201));
    String sessionId = created.path("id").asText();
    JsonNode storedSnapshot =
        objectMapper.readTree(
            jdbc.queryForObject(
                "SELECT setup_snapshot FROM cook_sessions WHERE id = ?",
                String.class,
                UUID.fromString(sessionId)));

    MvcResult activeResult =
        mockMvc
            .perform(
                get("/api/cook-sessions/active")
                    .header("Authorization", owner.authorization()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(sessionId))
            .andExpect(jsonPath("$.status").value("cooking"))
            .andExpect(jsonPath("$.revision").value(0))
            .andExpect(jsonPath("$.reviewState").value("not_eligible"))
            .andExpect(jsonPath("$.recipeId").value(RAMEN_ID))
            .andExpect(jsonPath("$.personalized").value(false))
            .andExpect(jsonPath("$.ingredients.length()").value(3))
            .andExpect(jsonPath("$.steps.length()").value(4))
            .andReturn();
    JsonNode active = json(activeResult);
    assertThat(active.path("ingredients")).isEqualTo(storedSnapshot.path("ingredients"));
    assertThat(active.path("steps")).isEqualTo(storedSnapshot.path("steps"));
    assertThat(active.path("personalized").asBoolean())
        .isEqualTo(storedSnapshot.path("personalized").asBoolean());
    assertThat(active.path("ingredients")).isEqualTo(created.path("ingredients"));
    assertThat(active.path("steps")).isEqualTo(created.path("steps"));

    mockMvc
        .perform(get("/api/cook-sessions/active").header("Authorization", other.authorization()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            get("/api/cook-sessions/{id}", sessionId)
                .header("Authorization", other.authorization()))
        .andExpect(status().isNotFound());

    MvcResult completedResult =
        mockMvc
            .perform(
                post("/api/cook-sessions/{id}/complete", sessionId)
                    .header("Authorization", owner.authorization())
                    .header("Idempotency-Key", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.reviewState").value("open"))
            .andReturn();
    JsonNode completed = json(completedResult);
    assertThat(completed.path("ingredients")).isEqualTo(storedSnapshot.path("ingredients"));
    assertThat(completed.path("steps")).isEqualTo(storedSnapshot.path("steps"));

    MvcResult terminalResult =
        mockMvc
            .perform(
                get("/api/cook-sessions/{id}", sessionId)
                    .header("Authorization", owner.authorization()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("completed"))
            .andReturn();
    JsonNode terminal = json(terminalResult);
    assertThat(terminal.path("ingredients")).isEqualTo(storedSnapshot.path("ingredients"));
    assertThat(terminal.path("steps")).isEqualTo(storedSnapshot.path("steps"));

    mockMvc
        .perform(get("/api/cook-sessions/active").header("Authorization", owner.authorization()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/cook-sessions/reviewable")
                .header("Authorization", owner.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(sessionId))
        .andExpect(jsonPath("$.status").value("completed"))
        .andExpect(jsonPath("$.reviewState").value("open"))
        .andExpect(jsonPath("$.ingredients").isArray())
        .andExpect(jsonPath("$.steps").isArray());
    mockMvc
        .perform(
            get("/api/cook-sessions/reviewable")
                .header("Authorization", other.authorization()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/cook-sessions/{id}/review-skip", sessionId)
                .header("Authorization", owner.authorization())
                .header("Idempotency-Key", UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewState").value("skipped"));
    mockMvc
        .perform(
            get("/api/cook-sessions/reviewable")
                .header("Authorization", owner.authorization()))
        .andExpect(status().isNoContent());
  }

  @Test
  void completeAndAbortRaceHasOnlyOneTerminalWinner() throws Exception {
    Credentials credentials = bootstrap();
    String sessionId = json(createSession(credentials, UUID.randomUUID(), null, 201)).path("id").asText();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    CompletableFuture<Integer> complete =
        terminalRaceRequest(credentials, sessionId, "complete", ready, start);
    CompletableFuture<Integer> abort =
        terminalRaceRequest(credentials, sessionId, "abort", ready, start);
    ready.await();
    start.countDown();

    List<Integer> statuses = List.of(complete.join(), abort.join());
    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    String state =
        jdbc.queryForObject(
            "SELECT status FROM cook_sessions WHERE id = ?",
            String.class,
            UUID.fromString(sessionId));
    assertThat(state).isIn("completed", "aborted");
  }

  private CompletableFuture<Integer> terminalRaceRequest(
      Credentials credentials,
      String sessionId,
      String action,
      CountDownLatch ready,
      CountDownLatch start) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            ready.countDown();
            start.await();
            return mockMvc
                .perform(
                    post("/api/cook-sessions/{id}/" + action, sessionId)
                        .header("Authorization", credentials.authorization())
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andReturn()
                .getResponse()
                .getStatus();
          } catch (Exception exception) {
            throw new RuntimeException(exception);
          }
        });
  }

  private Credentials bootstrap() throws Exception {
    UUID installId = UUID.randomUUID();
    MvcResult result =
        mockMvc
            .perform(
                post("/api/anonymous-installs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonString(Map.of("installId", installId))))
            .andExpect(status().isCreated())
            .andReturn();
    return new Credentials(installId, json(result).path("installToken").asText());
  }

  private MvcResult createSession(
      Credentials credentials,
      UUID key,
      String personalVersionId,
      int expectedStatus) throws Exception {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("recipeId", RAMEN_ID);
    if (personalVersionId != null) {
      body.put("personalVersionId", personalVersionId);
    }
    return mockMvc
        .perform(
            post("/api/cook-sessions")
                .header("Authorization", credentials.authorization())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonString(body)))
        .andExpect(status().is(expectedStatus))
        .andReturn();
  }

  private void complete(Credentials credentials, String sessionId, UUID key) throws Exception {
    mockMvc
        .perform(
            post("/api/cook-sessions/{id}/complete", sessionId)
                .header("Authorization", credentials.authorization())
                .header("Idempotency-Key", key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("completed"))
        .andExpect(jsonPath("$.reviewState").value("open"));
  }

  private MvcResult review(
      Credentials credentials,
      String sessionId,
      List<Map<String, Object>> signals) throws Exception {
    return mockMvc
        .perform(
            post("/api/cook-sessions/{id}/review", sessionId)
                .header("Authorization", credentials.authorization())
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonString(Map.of("rating", 3, "signals", signals))))
        .andExpect(status().isCreated())
        .andReturn();
  }

  private MvcResult approve(
      Credentials credentials,
      String proposalId,
      List<String> selectedItemIds,
      int pointerRevision) throws Exception {
    return mockMvc
        .perform(
            post("/api/personal-recipe-proposals/{id}/approve", proposalId)
                .header("Authorization", credentials.authorization())
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonString(
                        Map.of(
                            "selectedItemIds", selectedItemIds,
                            "expectedPointerRevision", pointerRevision))))
        .andExpect(status().isOk())
        .andReturn();
  }

  private String jsonString(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}

record Credentials(UUID installId, String token) {
  String authorization() {
    return "Bearer " + token;
  }
}
