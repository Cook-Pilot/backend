package com.cookpilot.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ApiModels {
  private ApiModels() {}

  record AnonymousInstallRequest(@NotNull UUID installId) {}

  record AnonymousInstallResponse(UUID installId, String installToken) {}

  record RecipeSummary(
      UUID id,
      String title,
      String description,
      String imageKey,
      int totalMinutes,
      String difficulty,
      String personalVersionSummary) {}

  record Ingredient(
      UUID id,
      String name,
      double amount,
      String unit,
      boolean required,
      int sortOrder,
      String personalizationRole,
      boolean personalizationLocked) {}

  record RecipeStep(
      UUID id,
      int stepIndex,
      String instruction,
      Integer timerSeconds,
      String cautionNote,
      boolean timerAdjustable,
      Integer timerMinSeconds,
      Integer timerMaxSeconds,
      String safetyTier,
      String startConfirmationLabel,
      String completionCue) {}

  /** An absolute, stable-ID based value in an approved immutable personal version. */
  record RecipeAdjustment(
      String key,
      String type,
      String label,
      UUID ingredientId,
      UUID stepId,
      Double effectiveAmount,
      Integer effectiveSeconds) {}

  record PersonalVersion(
      UUID id,
      int versionNumber,
      String title,
      String summary,
      UUID parentVersionId,
      int baseRecipeRevision,
      List<RecipeAdjustment> adjustments,
      Instant createdAt) {}

  record RecipeDetail(
      UUID id,
      String title,
      String description,
      String imageKey,
      int totalMinutes,
      String difficulty,
      double servings,
      int contentRevision,
      List<Ingredient> baseIngredients,
      List<RecipeStep> baseSteps,
      List<Ingredient> personalizedIngredients,
      List<RecipeStep> personalizedSteps,
      PersonalVersion personalVersion,
      int defaultPointerRevision) {}

  record CreateCookSessionRequest(@NotNull UUID recipeId, UUID personalVersionId) {}

  record CookSessionResponse(
      UUID id,
      String status,
      int revision,
      boolean personalized,
      UUID recipeId,
      UUID personalVersionId,
      List<Ingredient> ingredients,
      List<RecipeStep> steps) {}

  record CookSessionStateResponse(
      UUID id,
      String status,
      int revision,
      String reviewState,
      UUID recipeId,
      UUID personalVersionId,
      boolean personalized,
      List<Ingredient> ingredients,
      List<RecipeStep> steps) {}

  record RecordEventRequest(
      UUID clientEventId,
      @NotBlank @Size(max = 80) String eventType,
      UUID stepId,
      @NotBlank @Size(max = 30) String source,
      Map<String, Object> payload) {}

  /** Legacy-compatible input. The raw transcript is deliberately never persisted. */
  record VoiceTranscriptRequest(
      @NotBlank @Size(max = 500) String transcript,
      UUID stepId,
      @Size(max = 80) String routedIntent,
      Double confidence) {}

  record AiFeedbackRequest(
      @NotNull UUID cookSessionId,
      @NotBlank @Size(max = 500) String userSpeech,
      @Min(0) int stepIndex,
      @Min(0) Integer remainingSeconds) {}

  record SuggestedAction(String type, Integer seconds) {}

  record AiFeedbackResponse(
      String speechText,
      String screenText,
      SuggestedAction suggestedAction,
      boolean offlineFallback) {}

  record ReviewSignal(@NotBlank @Size(max = 40) String tag, UUID stepId) {}

  record PostCookReviewRequest(
      @Min(1) @Max(5) int rating,
      List<@NotNull @Valid ReviewSignal> signals,
      @Size(max = 1000) String comment,
      @Size(max = 500) String nextTimeNote) {}

  record ProposalItem(
      UUID id,
      String key,
      String type,
      String label,
      UUID ingredientId,
      UUID stepId,
      Double beforeAmount,
      Double proposedAmount,
      Integer beforeSeconds,
      Integer proposedSeconds,
      String decision) {}

  record PersonalRecipeProposal(
      UUID id,
      UUID reviewId,
      UUID recipeId,
      UUID parentVersionId,
      int baseRecipeRevision,
      String status,
      List<ProposalItem> items,
      Instant createdAt) {}

  record PostCookReviewResponse(
      UUID reviewId, String result, PersonalRecipeProposal proposal) {}

  record ApproveProposalRequest(
      @NotEmpty List<@NotNull UUID> selectedItemIds,
      @NotNull @Min(0) Integer expectedPointerRevision) {}

  record ProposalDecisionResponse(
      UUID proposalId,
      String status,
      PersonalVersion version,
      Integer pointerRevision) {}

  record RollbackRequest(
      @NotNull UUID expectedCurrentVersionId,
      @NotNull @Min(0) Integer expectedPointerRevision) {}

  record DefaultVersionResponse(PersonalVersion defaultVersion, int pointerRevision) {}

  record HealthResponse(String status, String storage, boolean aiConfigured, Instant timestamp) {}

  record ErrorResponse(
      String code,
      String message,
      String requestId,
      boolean retryable,
      Map<String, String> fieldErrors,
      Instant timestamp) {}
}
