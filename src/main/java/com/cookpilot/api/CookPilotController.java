package com.cookpilot.api;

import com.cookpilot.api.ApiModels.AiFeedbackRequest;
import com.cookpilot.api.ApiModels.AiFeedbackResponse;
import com.cookpilot.api.ApiModels.AnonymousInstallRequest;
import com.cookpilot.api.ApiModels.AnonymousInstallResponse;
import com.cookpilot.api.ApiModels.ApproveProposalRequest;
import com.cookpilot.api.ApiModels.CookSessionResponse;
import com.cookpilot.api.ApiModels.CookSessionStateResponse;
import com.cookpilot.api.ApiModels.CreateCookSessionRequest;
import com.cookpilot.api.ApiModels.DefaultVersionResponse;
import com.cookpilot.api.ApiModels.HealthResponse;
import com.cookpilot.api.ApiModels.PersonalRecipeProposal;
import com.cookpilot.api.ApiModels.PostCookReviewRequest;
import com.cookpilot.api.ApiModels.PostCookReviewResponse;
import com.cookpilot.api.ApiModels.ProposalDecisionResponse;
import com.cookpilot.api.ApiModels.RecordEventRequest;
import com.cookpilot.api.ApiModels.RecipeDetail;
import com.cookpilot.api.ApiModels.RecipeSummary;
import com.cookpilot.api.ApiModels.RollbackRequest;
import com.cookpilot.api.ApiModels.VoiceTranscriptRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class CookPilotController {
  private static final String AUTHORIZATION = "Authorization";
  private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  private final InstallPrincipalService installs;
  private final IdempotencyService idempotency;
  private final RecipeRepository recipes;
  private final CookSessionRepository sessions;
  private final CoachService coach;
  private final ReviewService reviews;
  private final PersonalRecipeService personalRecipes;
  private final DatabaseJson databaseJson;

  CookPilotController(
      InstallPrincipalService installs,
      IdempotencyService idempotency,
      RecipeRepository recipes,
      CookSessionRepository sessions,
      CoachService coach,
      ReviewService reviews,
      PersonalRecipeService personalRecipes,
      DatabaseJson databaseJson) {
    this.installs = installs;
    this.idempotency = idempotency;
    this.recipes = recipes;
    this.sessions = sessions;
    this.coach = coach;
    this.reviews = reviews;
    this.personalRecipes = personalRecipes;
    this.databaseJson = databaseJson;
  }

  @GetMapping("/health")
  HealthResponse health() {
    return new HealthResponse(
        "ok", databaseJson.isPostgres() ? "postgres" : "h2-local", coach.isAiConfigured(), Instant.now());
  }

  @PostMapping("/anonymous-installs")
  @ResponseStatus(HttpStatus.CREATED)
  AnonymousInstallResponse bootstrap(@Valid @RequestBody AnonymousInstallRequest request) {
    return installs.bootstrap(request);
  }

  @GetMapping("/recipes")
  List<RecipeSummary> recipes(@RequestHeader(value = AUTHORIZATION, required = false) String authorization) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return recipes.findSummaries(principal.installId());
  }

  @GetMapping("/recipes/{recipeId}")
  RecipeDetail recipe(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @PathVariable UUID recipeId) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return recipes.findDetail(recipeId, principal.installId());
  }

  @PostMapping("/cook-sessions")
  @ResponseStatus(HttpStatus.CREATED)
  CookSessionResponse createSession(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
      @Valid @RequestBody CreateCookSessionRequest request) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return idempotency.execute(
        principal,
        key,
        "create-cook-session",
        request,
        201,
        CookSessionResponse.class,
        () -> sessions.create(principal, request));
  }

  @GetMapping("/cook-sessions/active")
  ResponseEntity<CookSessionStateResponse> activeSession(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return sessions.findActive(principal).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/cook-sessions/reviewable")
  ResponseEntity<CookSessionStateResponse> reviewableSession(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return sessions.findReviewable(principal).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/cook-sessions/{sessionId}")
  CookSessionStateResponse session(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @PathVariable UUID sessionId) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return sessions.findState(principal, sessionId);
  }

  @PostMapping("/cook-sessions/{sessionId}/events")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map<String, String> recordEvent(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @PathVariable UUID sessionId,
      @Valid @RequestBody RecordEventRequest request) {
    InstallPrincipal principal = installs.authenticate(authorization);
    sessions.recordEvent(principal, sessionId, request);
    return Map.of("status", "recorded");
  }

  @PostMapping("/cook-sessions/{sessionId}/transcripts")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map<String, String> recordTranscriptIntent(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @PathVariable UUID sessionId,
      @Valid @RequestBody VoiceTranscriptRequest request) {
    InstallPrincipal principal = installs.authenticate(authorization);
    sessions.recordTranscriptIntent(principal, sessionId, request);
    return Map.of("status", "intent_recorded", "rawTranscript", "not_stored");
  }

  @PostMapping("/cook-sessions/{sessionId}/complete")
  CookSessionStateResponse complete(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
      @PathVariable UUID sessionId) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return idempotency.execute(
        principal,
        key,
        "complete-cook-session:" + sessionId,
        Map.of("sessionId", sessionId),
        200,
        CookSessionStateResponse.class,
        () -> sessions.complete(principal, sessionId));
  }

  @PostMapping("/cook-sessions/{sessionId}/abort")
  CookSessionStateResponse abort(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
      @PathVariable UUID sessionId) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return idempotency.execute(
        principal,
        key,
        "abort-cook-session:" + sessionId,
        Map.of("sessionId", sessionId),
        200,
        CookSessionStateResponse.class,
        () -> sessions.abort(principal, sessionId));
  }

  @PostMapping("/cook-sessions/{sessionId}/review-skip")
  CookSessionStateResponse skipReview(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
      @PathVariable UUID sessionId) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return idempotency.execute(
        principal,
        key,
        "skip-review:" + sessionId,
        Map.of("sessionId", sessionId),
        200,
        CookSessionStateResponse.class,
        () -> sessions.skipReview(principal, sessionId));
  }

  @PostMapping("/ai/feedback")
  AiFeedbackResponse feedback(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @Valid @RequestBody AiFeedbackRequest request) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return coach.answer(principal, request);
  }

  @PostMapping("/cook-sessions/{sessionId}/review")
  @ResponseStatus(HttpStatus.CREATED)
  PostCookReviewResponse review(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
      @PathVariable UUID sessionId,
      @Valid @RequestBody PostCookReviewRequest request) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return idempotency.execute(
        principal,
        key,
        "submit-review:" + sessionId,
        request,
        201,
        PostCookReviewResponse.class,
        () -> reviews.submit(principal, sessionId, request));
  }

  @GetMapping("/personal-recipe-proposals")
  List<PersonalRecipeProposal> pendingProposals(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return personalRecipes.findPending(principal);
  }

  @PostMapping("/personal-recipe-proposals/{proposalId}/approve")
  ProposalDecisionResponse approveProposal(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
      @PathVariable UUID proposalId,
      @Valid @RequestBody ApproveProposalRequest request) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return idempotency.execute(
        principal,
        key,
        "approve-proposal:" + proposalId,
        request,
        200,
        ProposalDecisionResponse.class,
        () -> personalRecipes.approve(principal, proposalId, request));
  }

  @PostMapping("/personal-recipe-proposals/{proposalId}/reject")
  ProposalDecisionResponse rejectProposal(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
      @PathVariable UUID proposalId) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return idempotency.execute(
        principal,
        key,
        "reject-proposal:" + proposalId,
        Map.of("proposalId", proposalId),
        200,
        ProposalDecisionResponse.class,
        () -> personalRecipes.reject(principal, proposalId));
  }

  @PostMapping("/personal-recipes/{recipeId}/default-version/rollback")
  DefaultVersionResponse rollback(
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
      @PathVariable UUID recipeId,
      @Valid @RequestBody RollbackRequest request) {
    InstallPrincipal principal = installs.authenticate(authorization);
    return idempotency.execute(
        principal,
        key,
        "rollback-personal-version:" + recipeId,
        request,
        200,
        DefaultVersionResponse.class,
        () -> personalRecipes.rollback(principal, recipeId, request));
  }
}
