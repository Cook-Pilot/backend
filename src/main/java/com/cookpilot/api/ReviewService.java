package com.cookpilot.api;

import com.cookpilot.api.ApiModels.PersonalRecipeProposal;
import com.cookpilot.api.ApiModels.PostCookReviewRequest;
import com.cookpilot.api.ApiModels.PostCookReviewResponse;
import com.cookpilot.api.ApiModels.ProposalItem;
import com.cookpilot.api.ApiModels.RecipeDetail;
import com.cookpilot.api.ApiModels.ReviewSignal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class ReviewService {
  private static final Set<String> ALLOWED_TAGS =
      Set.of("JUST_RIGHT", "TOO_SALTY", "BLAND", "TOO_SPICY", "TOO_WATERY", "TOO_SOFT", "UNDERCOOKED");

  private final JdbcTemplate jdbc;
  private final RecipeRepository recipes;
  private final CookSessionRepository sessions;
  private final PersonalizationEngine personalization;
  private final DatabaseJson databaseJson;

  ReviewService(
      JdbcTemplate jdbc,
      RecipeRepository recipes,
      CookSessionRepository sessions,
      PersonalizationEngine personalization,
      DatabaseJson databaseJson) {
    this.jdbc = jdbc;
    this.recipes = recipes;
    this.sessions = sessions;
    this.personalization = personalization;
    this.databaseJson = databaseJson;
  }

  @Transactional
  PostCookReviewResponse submit(
      InstallPrincipal principal, UUID sessionId, PostCookReviewRequest request) {
    List<ReviewSignal> signals = request.signals() == null ? List.of() : request.signals();
    validateSignals(signals);
    ReviewableSession session = sessions.claimForReview(principal, sessionId);
    RecipeDetail recipe = recipes.findDetail(session.recipeId(), principal.installId());
    validateStepTargets(signals, recipe);
    recipes.ensurePointer(principal.installId(), recipe.id());

    UUID reviewId = UUID.randomUUID();
    Instant now = Instant.now();
    Map<String, Object> structured = new LinkedHashMap<>();
    structured.put("signals", signals);
    jdbc.update(
        """
        INSERT INTO post_cook_reviews
          (id, cook_session_id, user_id, recipe_id, rating, comment, next_time_note,
           structured_feedback, generation_status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?)
        """,
        reviewId,
        sessionId,
        principal.userId(),
        recipe.id(),
        request.rating(),
        request.comment(),
        request.nextTimeNote(),
        databaseJson.value(structured),
        Timestamp.from(now));

    List<ProposalDraftItem> draftItems =
        personalization.createProposal(
            recipe.baseIngredients(),
            recipe.personalizedIngredients(),
            recipe.baseSteps(),
            recipe.personalizedSteps(),
            signals);
    if (draftItems.isEmpty()) {
      jdbc.update(
          "UPDATE post_cook_reviews SET generation_status = 'no_change' WHERE id = ?",
          reviewId);
      return new PostCookReviewResponse(reviewId, "NO_CHANGE", null);
    }

    UUID proposalId = UUID.randomUUID();
    UUID parentVersionId = recipe.personalVersion() == null ? null : recipe.personalVersion().id();
    jdbc.update(
        """
        INSERT INTO personal_recipe_proposals
          (id, review_id, install_id, recipe_id, parent_version_id,
           base_recipe_revision, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, 'pending', ?)
        """,
        proposalId,
        reviewId,
        principal.installId(),
        recipe.id(),
        parentVersionId,
        recipe.contentRevision(),
        Timestamp.from(now));

    List<ProposalItem> responseItems =
        draftItems.stream().map(item -> insertProposalItem(proposalId, item)).toList();
    jdbc.update(
        "UPDATE post_cook_reviews SET generation_status = 'proposal_ready' WHERE id = ?",
        reviewId);
    PersonalRecipeProposal proposal =
        new PersonalRecipeProposal(
            proposalId,
            reviewId,
            recipe.id(),
            parentVersionId,
            recipe.contentRevision(),
            "pending",
            responseItems,
            now);
    return new PostCookReviewResponse(reviewId, "PROPOSAL_READY", proposal);
  }

  private ProposalItem insertProposalItem(UUID proposalId, ProposalDraftItem item) {
    UUID itemId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO personal_recipe_proposal_items
          (id, proposal_id, item_key, item_type, label, ingredient_id, step_id,
           before_amount, proposed_amount, before_seconds, proposed_seconds)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        itemId,
        proposalId,
        item.itemKey(),
        item.itemType(),
        item.label(),
        item.ingredientId(),
        item.stepId(),
        item.beforeAmount(),
        item.proposedAmount(),
        item.beforeSeconds(),
        item.proposedSeconds());
    return new ProposalItem(
        itemId,
        item.itemKey(),
        item.itemType(),
        item.label(),
        item.ingredientId(),
        item.stepId(),
        item.beforeAmount(),
        item.proposedAmount(),
        item.beforeSeconds(),
        item.proposedSeconds(),
        null);
  }

  private void validateSignals(List<ReviewSignal> signals) {
    for (ReviewSignal signal : signals) {
      String tag = signal.tag() == null ? "" : signal.tag().trim().toUpperCase();
      if (!ALLOWED_TAGS.contains(tag)) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "지원하지 않는 후기 신호입니다.");
      }
      boolean needsStep = Set.of("TOO_SOFT", "UNDERCOOKED").contains(tag);
      if (needsStep != (signal.stepId() != null)) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "후기 신호의 단계 ID가 유효하지 않습니다.");
      }
    }
  }

  private void validateStepTargets(List<ReviewSignal> signals, RecipeDetail recipe) {
    Set<UUID> stepIds = recipe.baseSteps().stream().map(ApiModels.RecipeStep::id).collect(java.util.stream.Collectors.toSet());
    if (signals.stream().filter(signal -> signal.stepId() != null).anyMatch(signal -> !stepIds.contains(signal.stepId()))) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "후기 단계가 이 레시피에 속하지 않습니다.");
    }
  }
}
