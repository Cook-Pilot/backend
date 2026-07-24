package com.cookpilot.api;

import com.cookpilot.api.ApiModels.ApproveProposalRequest;
import com.cookpilot.api.ApiModels.DefaultVersionResponse;
import com.cookpilot.api.ApiModels.PersonalRecipeProposal;
import com.cookpilot.api.ApiModels.PersonalVersion;
import com.cookpilot.api.ApiModels.ProposalDecisionResponse;
import com.cookpilot.api.ApiModels.ProposalItem;
import com.cookpilot.api.ApiModels.RecipeDetail;
import com.cookpilot.api.ApiModels.RollbackRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class PersonalRecipeService {
  private final JdbcTemplate jdbc;
  private final RecipeRepository recipes;

  PersonalRecipeService(JdbcTemplate jdbc, RecipeRepository recipes) {
    this.jdbc = jdbc;
    this.recipes = recipes;
  }

  List<PersonalRecipeProposal> findPending(InstallPrincipal principal) {
    return jdbc.query(
            """
            SELECT id, review_id, recipe_id, parent_version_id, base_recipe_revision,
                   status, created_at
            FROM personal_recipe_proposals
            WHERE install_id = ? AND status = 'pending'
            ORDER BY created_at
            """,
            (resultSet, rowNumber) ->
                new ProposalRow(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("review_id", UUID.class),
                    resultSet.getObject("recipe_id", UUID.class),
                    resultSet.getObject("parent_version_id", UUID.class),
                    resultSet.getInt("base_recipe_revision"),
                    resultSet.getString("status"),
                    resultSet.getTimestamp("created_at").toInstant()),
            principal.installId())
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  ProposalDecisionResponse approve(
      InstallPrincipal principal, UUID proposalId, ApproveProposalRequest request) {
    ProposalRow proposal = lockProposal(principal, proposalId);
    if (!"pending".equals(proposal.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 변경안입니다.");
    }
    RecipeBase recipe = recipes.findBase(proposal.recipeId());
    if (recipe.contentRevision() != proposal.baseRecipeRevision()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "레시피가 변경되어 변경안을 다시 확인해야 합니다.");
    }
    DefaultPointer pointer = recipes.lockPointer(principal.installId(), proposal.recipeId());
    if (pointer.revision() != request.expectedPointerRevision()
        || !Objects.equals(pointer.defaultVersionId(), proposal.parentVersionId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 개인 레시피 변경이 먼저 저장되었습니다.");
    }

    List<ProposalItemRow> items = findItemRows(proposalId);
    Set<UUID> selected = new HashSet<>(request.selectedItemIds());
    if (selected.size() != request.selectedItemIds().size()
        || selected.isEmpty()
        || !items.stream().map(ProposalItemRow::id).collect(java.util.stream.Collectors.toSet()).containsAll(selected)) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "승인할 변경 항목이 유효하지 않습니다.");
    }

    int versionNumber = recipes.nextVersionNumber(principal.installId(), proposal.recipeId());
    UUID versionId = UUID.randomUUID();
    List<ProposalItemRow> applied = items.stream().filter(item -> selected.contains(item.id())).toList();
    validateAppliedItems(principal, proposal, applied);
    String labels = applied.stream().map(ProposalItemRow::label).reduce((left, right) -> left + ", " + right).orElse("");
    String summary = recipe.title() + "에 " + labels + "를 반영했어요.";
    recipes.insertVersion(
        versionId,
        principal,
        proposal.recipeId(),
        versionNumber,
        recipe.title() + " - 내 버전 " + versionNumber,
        summary,
        proposal.parentVersionId(),
        proposalId,
        proposal.baseRecipeRevision());
    recipes.copyVersionItems(proposal.parentVersionId(), versionId);
    applied.forEach(item -> recipes.replaceVersionItem(versionId, item));

    jdbc.update(
        "UPDATE personal_recipe_proposal_items SET decision = 'kept' WHERE proposal_id = ?",
        proposalId);
    for (UUID itemId : selected) {
      jdbc.update(
          "UPDATE personal_recipe_proposal_items SET decision = 'applied' WHERE proposal_id = ? AND id = ?",
          proposalId,
          itemId);
    }
    jdbc.update(
        """
        UPDATE personal_recipe_proposals
        SET status = 'approved', decided_at = ?
        WHERE id = ? AND status = 'pending'
        """,
        Timestamp.from(Instant.now()),
        proposalId);
    recipes.updatePointer(
        principal.installId(),
        proposal.recipeId(),
        versionId,
        request.expectedPointerRevision());
    PersonalVersion version =
        recipes.findVersionRequired(versionId, principal.installId(), proposal.recipeId());
    return new ProposalDecisionResponse(
        proposalId, "approved", version, request.expectedPointerRevision() + 1);
  }

  @Transactional
  ProposalDecisionResponse reject(InstallPrincipal principal, UUID proposalId) {
    ProposalRow proposal = lockProposal(principal, proposalId);
    if (!"pending".equals(proposal.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 변경안입니다.");
    }
    jdbc.update(
        "UPDATE personal_recipe_proposal_items SET decision = 'kept' WHERE proposal_id = ?",
        proposalId);
    jdbc.update(
        "UPDATE personal_recipe_proposals SET status = 'rejected', decided_at = ? WHERE id = ?",
        Timestamp.from(Instant.now()),
        proposalId);
    return new ProposalDecisionResponse(proposalId, "rejected", null, null);
  }

  @Transactional
  DefaultVersionResponse rollback(
      InstallPrincipal principal, UUID recipeId, RollbackRequest request) {
    recipes.findBase(recipeId);
    DefaultPointer pointer = recipes.lockPointer(principal.installId(), recipeId);
    if (pointer.revision() != request.expectedPointerRevision()
        || !Objects.equals(pointer.defaultVersionId(), request.expectedCurrentVersionId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "현재 개인 레시피 버전이 변경되었습니다.");
    }
    PersonalVersion current =
        recipes.findVersionRequired(request.expectedCurrentVersionId(), principal.installId(), recipeId);
    UUID parentId = current.parentVersionId();
    recipes.updatePointer(
        principal.installId(), recipeId, parentId, request.expectedPointerRevision());
    PersonalVersion parent =
        parentId == null ? null : recipes.findVersionRequired(parentId, principal.installId(), recipeId);
    return new DefaultVersionResponse(parent, request.expectedPointerRevision() + 1);
  }

  private ProposalRow lockProposal(InstallPrincipal principal, UUID proposalId) {
    return jdbc.query(
            """
            SELECT id, review_id, recipe_id, parent_version_id, base_recipe_revision,
                   status, created_at
            FROM personal_recipe_proposals
            WHERE id = ? AND install_id = ?
            FOR UPDATE
            """,
            (resultSet, rowNumber) ->
                new ProposalRow(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("review_id", UUID.class),
                    resultSet.getObject("recipe_id", UUID.class),
                    resultSet.getObject("parent_version_id", UUID.class),
                    resultSet.getInt("base_recipe_revision"),
                    resultSet.getString("status"),
                    resultSet.getTimestamp("created_at").toInstant()),
            proposalId,
            principal.installId())
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "개인 레시피 변경안을 찾을 수 없습니다."));
  }

  private PersonalRecipeProposal toResponse(ProposalRow row) {
    return new PersonalRecipeProposal(
        row.id(),
        row.reviewId(),
        row.recipeId(),
        row.parentVersionId(),
        row.baseRecipeRevision(),
        row.status(),
        findItemRows(row.id()).stream().map(this::toResponse).toList(),
        row.createdAt());
  }

  private ProposalItem toResponse(ProposalItemRow item) {
    return new ProposalItem(
        item.id(),
        item.itemKey(),
        item.itemType(),
        item.label(),
        item.ingredientId(),
        item.stepId(),
        item.beforeAmount(),
        item.proposedAmount(),
        item.beforeSeconds(),
        item.proposedSeconds(),
        item.decision());
  }

  private void validateAppliedItems(
      InstallPrincipal principal, ProposalRow proposal, List<ProposalItemRow> items) {
    RecipeDetail detail = recipes.findDetail(proposal.recipeId(), principal.installId());
    MapById lookup = new MapById(detail);
    for (ProposalItemRow item : items) {
      if ("ingredient_amount".equals(item.itemType())) {
        ApiModels.Ingredient base = lookup.baseIngredient(item.ingredientId());
        ApiModels.Ingredient effective = lookup.effectiveIngredient(item.ingredientId());
        if (base.personalizationLocked()
            || "none".equals(base.personalizationRole())
            || item.proposedAmount() == null
            || item.beforeAmount() == null
            || Math.abs(item.beforeAmount() - effective.amount()) > 0.001
            || item.proposedAmount() < base.amount() * 0.70 - 0.001
            || item.proposedAmount() > base.amount() * 1.30 + 0.001
            || Math.abs(item.proposedAmount() - item.beforeAmount()) > Math.abs(item.beforeAmount()) * 0.15 + 0.001) {
          throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "안전 범위를 벗어난 재료 변경입니다.");
        }
      } else if ("step_timer".equals(item.itemType())) {
        ApiModels.RecipeStep base = lookup.baseStep(item.stepId());
        ApiModels.RecipeStep effective = lookup.effectiveStep(item.stepId());
        int lower = base.timerMinSeconds() == null ? Math.max(15, base.timerSeconds() - 120) : base.timerMinSeconds();
        int upper = base.timerMaxSeconds() == null ? base.timerSeconds() + 120 : base.timerMaxSeconds();
        if (!base.timerAdjustable()
            || !"normal".equals(base.safetyTier())
            || base.cautionNote() != null
            || item.proposedSeconds() == null
            || item.beforeSeconds() == null
            || !item.beforeSeconds().equals(effective.timerSeconds())
            || item.proposedSeconds() < lower
            || item.proposedSeconds() > upper
            || Math.abs(item.proposedSeconds() - base.timerSeconds()) > 120) {
          throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "안전 범위를 벗어난 타이머 변경입니다.");
        }
      } else {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "지원하지 않는 변경 항목입니다.");
      }
    }
  }

  private List<ProposalItemRow> findItemRows(UUID proposalId) {
    return jdbc.query(
        """
        SELECT id, item_key, item_type, label, ingredient_id, step_id,
               before_amount, proposed_amount, before_seconds, proposed_seconds, decision
        FROM personal_recipe_proposal_items
        WHERE proposal_id = ?
        ORDER BY item_key
        """,
        (resultSet, rowNumber) ->
            new ProposalItemRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("item_key"),
                resultSet.getString("item_type"),
                resultSet.getString("label"),
                resultSet.getObject("ingredient_id", UUID.class),
                resultSet.getObject("step_id", UUID.class),
                resultSet.getObject("before_amount", Double.class),
                resultSet.getObject("proposed_amount", Double.class),
                resultSet.getObject("before_seconds", Integer.class),
                resultSet.getObject("proposed_seconds", Integer.class),
                resultSet.getString("decision")),
        proposalId);
  }
}

final class MapById {
  private final java.util.Map<UUID, ApiModels.Ingredient> baseIngredients;
  private final java.util.Map<UUID, ApiModels.Ingredient> effectiveIngredients;
  private final java.util.Map<UUID, ApiModels.RecipeStep> baseSteps;
  private final java.util.Map<UUID, ApiModels.RecipeStep> effectiveSteps;

  MapById(ApiModels.RecipeDetail detail) {
    baseIngredients = detail.baseIngredients().stream().collect(java.util.stream.Collectors.toMap(ApiModels.Ingredient::id, item -> item));
    effectiveIngredients = detail.personalizedIngredients().stream().collect(java.util.stream.Collectors.toMap(ApiModels.Ingredient::id, item -> item));
    baseSteps = detail.baseSteps().stream().collect(java.util.stream.Collectors.toMap(ApiModels.RecipeStep::id, item -> item));
    effectiveSteps = detail.personalizedSteps().stream().collect(java.util.stream.Collectors.toMap(ApiModels.RecipeStep::id, item -> item));
  }

  ApiModels.Ingredient baseIngredient(UUID id) {
    ApiModels.Ingredient value = baseIngredients.get(id);
    if (value == null) throw invalidTarget();
    return value;
  }

  ApiModels.Ingredient effectiveIngredient(UUID id) {
    ApiModels.Ingredient value = effectiveIngredients.get(id);
    if (value == null) throw invalidTarget();
    return value;
  }

  ApiModels.RecipeStep baseStep(UUID id) {
    ApiModels.RecipeStep value = baseSteps.get(id);
    if (value == null) throw invalidTarget();
    return value;
  }

  ApiModels.RecipeStep effectiveStep(UUID id) {
    ApiModels.RecipeStep value = effectiveSteps.get(id);
    if (value == null) throw invalidTarget();
    return value;
  }

  private ResponseStatusException invalidTarget() {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "변경 대상이 이 레시피에 속하지 않습니다.");
  }
}

record ProposalRow(
    UUID id,
    UUID reviewId,
    UUID recipeId,
    UUID parentVersionId,
    int baseRecipeRevision,
    String status,
    Instant createdAt) {}

record ProposalItemRow(
    UUID id,
    String itemKey,
    String itemType,
    String label,
    UUID ingredientId,
    UUID stepId,
    Double beforeAmount,
    Double proposedAmount,
    Integer beforeSeconds,
    Integer proposedSeconds,
    String decision) {}
