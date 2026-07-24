package com.cookpilot.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.cookpilot.api.ApiModels.Ingredient;
import com.cookpilot.api.ApiModels.RecipeAdjustment;
import com.cookpilot.api.ApiModels.RecipeStep;
import com.cookpilot.api.ApiModels.ReviewSignal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalizationEngineTest {
  private final PersonalizationEngine engine = new PersonalizationEngine();

  @Test
  void createsStableIdAbsoluteProposalsAndAppliesApprovedValues() {
    UUID seasoningId = UUID.randomUUID();
    UUID stepId = UUID.randomUUID();
    List<Ingredient> baseIngredients =
        List.of(
            ingredient(UUID.randomUUID(), "물", 500, "liquid"),
            ingredient(seasoningId, "분말스프", 1, "salty"));
    List<Ingredient> effectiveIngredients =
        List.of(
            ingredient(baseIngredients.get(0).id(), "물", 500, "liquid"),
            ingredient(seasoningId, "분말스프", 0.9, "salty"));
    List<RecipeStep> baseSteps = List.of(step(stepId, 180, true, "normal"));
    List<RecipeStep> effectiveSteps = List.of(step(stepId, 150, true, "normal"));

    List<ProposalDraftItem> proposal =
        engine.createProposal(
            baseIngredients,
            effectiveIngredients,
            baseSteps,
            effectiveSteps,
            List.of(new ReviewSignal("TOO_SALTY", null), new ReviewSignal("TOO_SOFT", stepId)));

    assertThat(proposal).hasSize(2);
    assertThat(proposal)
        .anySatisfy(
            item -> {
              assertThat(item.itemKey()).isEqualTo("ingredient_amount:" + seasoningId);
              assertThat(item.beforeAmount()).isEqualTo(0.9);
              assertThat(item.proposedAmount()).isEqualTo(0.81);
            })
        .anySatisfy(
            item -> {
              assertThat(item.itemKey()).isEqualTo("step_timer:" + stepId);
              assertThat(item.beforeSeconds()).isEqualTo(150);
              assertThat(item.proposedSeconds()).isEqualTo(120);
            });

    List<RecipeAdjustment> approved =
        proposal.stream()
            .map(
                item ->
                    new RecipeAdjustment(
                        item.itemKey(),
                        item.itemType(),
                        item.label(),
                        item.ingredientId(),
                        item.stepId(),
                        item.proposedAmount(),
                        item.proposedSeconds()))
            .toList();
    assertThat(engine.applyIngredients(baseIngredients, approved).get(1).amount()).isEqualTo(0.81);
    assertThat(engine.applySteps(baseSteps, approved).getFirst().timerSeconds()).isEqualTo(120);
  }

  @Test
  void undercookedAndSafetyLockedStepsNeverCreateAProposal() {
    UUID stepId = UUID.randomUUID();
    RecipeStep blocked = step(stepId, 60, false, "blocked");

    List<ProposalDraftItem> proposal =
        engine.createProposal(
            List.of(),
            List.of(),
            List.of(blocked),
            List.of(blocked),
            List.of(new ReviewSignal("UNDERCOOKED", stepId), new ReviewSignal("TOO_SOFT", stepId)));

    assertThat(proposal).isEmpty();
  }

  private Ingredient ingredient(UUID id, String name, double amount, String role) {
    return new Ingredient(id, name, amount, "g", true, 1, role, false);
  }

  private RecipeStep step(UUID id, int seconds, boolean adjustable, String safetyTier) {
    return new RecipeStep(
        id,
        0,
        "재료를 익히세요.",
        seconds,
        null,
        adjustable,
        15,
        300,
        safetyTier,
        "재료를 넣었어요 · 시작",
        "익힘을 확인");
  }
}
