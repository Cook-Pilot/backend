package com.cookpilot.api;

import com.cookpilot.api.ApiModels.Ingredient;
import com.cookpilot.api.ApiModels.RecipeAdjustment;
import com.cookpilot.api.ApiModels.RecipeStep;
import com.cookpilot.api.ApiModels.ReviewSignal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PersonalizationEngine {
  List<ProposalDraftItem> createProposal(
      List<Ingredient> baseIngredients,
      List<Ingredient> effectiveIngredients,
      List<RecipeStep> baseSteps,
      List<RecipeStep> effectiveSteps,
      List<ReviewSignal> signals) {
    Map<UUID, Ingredient> effectiveIngredientById = byIngredientId(effectiveIngredients);
    Map<UUID, RecipeStep> effectiveStepById = byStepId(effectiveSteps);
    Map<String, ProposalDraftItem> unique = new LinkedHashMap<>();

    for (ReviewSignal signal : signals == null ? List.<ReviewSignal>of() : signals) {
      String tag = signal.tag().trim().toUpperCase(Locale.ROOT);
      switch (tag) {
        case "TOO_SALTY" ->
            ingredientChange(baseIngredients, effectiveIngredientById, "salty", 0.90, "간을 10% 줄이기")
                .ifPresent(item -> unique.putIfAbsent(item.itemKey(), item));
        case "BLAND" ->
            ingredientChange(baseIngredients, effectiveIngredientById, "salty", 1.10, "간을 10% 늘리기")
                .ifPresent(item -> unique.putIfAbsent(item.itemKey(), item));
        case "TOO_SPICY" ->
            ingredientChange(baseIngredients, effectiveIngredientById, "spicy", 0.90, "매운맛을 10% 줄이기")
                .ifPresent(item -> unique.putIfAbsent(item.itemKey(), item));
        case "TOO_WATERY" ->
            ingredientChange(baseIngredients, effectiveIngredientById, "liquid", 0.90, "물을 10% 줄이기")
                .ifPresent(item -> unique.putIfAbsent(item.itemKey(), item));
        case "TOO_SOFT" ->
            stepChange(baseSteps, effectiveStepById, signal.stepId(), -30)
                .ifPresent(item -> unique.putIfAbsent(item.itemKey(), item));
        case "JUST_RIGHT", "UNDERCOOKED" -> {
          // UNDERCOOKED is safety-adjacent. Keep it as review evidence only.
        }
        default -> {
          // Unknown tags are ignored here and rejected by the service validation boundary.
        }
      }
    }
    return List.copyOf(unique.values());
  }

  List<Ingredient> applyIngredients(List<Ingredient> base, List<RecipeAdjustment> adjustments) {
    Map<UUID, Double> values = new LinkedHashMap<>();
    adjustments.stream()
        .filter(item -> "ingredient_amount".equals(item.type()))
        .filter(item -> item.ingredientId() != null && item.effectiveAmount() != null)
        .forEach(item -> values.put(item.ingredientId(), item.effectiveAmount()));
    return base.stream()
        .map(
            ingredient -> {
              Double amount = values.get(ingredient.id());
              if (amount == null) {
                return ingredient;
              }
              return new Ingredient(
                  ingredient.id(),
                  ingredient.name(),
                  amount,
                  ingredient.unit(),
                  ingredient.required(),
                  ingredient.sortOrder(),
                  ingredient.personalizationRole(),
                  ingredient.personalizationLocked());
            })
        .toList();
  }

  List<RecipeStep> applySteps(List<RecipeStep> base, List<RecipeAdjustment> adjustments) {
    Map<UUID, Integer> values = new LinkedHashMap<>();
    adjustments.stream()
        .filter(item -> "step_timer".equals(item.type()))
        .filter(item -> item.stepId() != null && item.effectiveSeconds() != null)
        .forEach(item -> values.put(item.stepId(), item.effectiveSeconds()));
    return base.stream()
        .map(
            step -> {
              Integer seconds = values.get(step.id());
              if (seconds == null) {
                return step;
              }
              return new RecipeStep(
                  step.id(),
                  step.stepIndex(),
                  step.instruction(),
                  seconds,
                  step.cautionNote(),
                  step.timerAdjustable(),
                  step.timerMinSeconds(),
                  step.timerMaxSeconds(),
                  step.safetyTier(),
                  step.startConfirmationLabel(),
                  step.completionCue());
            })
        .toList();
  }

  private java.util.Optional<ProposalDraftItem> ingredientChange(
      List<Ingredient> baseIngredients,
      Map<UUID, Ingredient> effectiveById,
      String role,
      double multiplier,
      String actionLabel) {
    return baseIngredients.stream()
        .filter(item -> role.equals(item.personalizationRole()))
        .filter(item -> !item.personalizationLocked())
        .findFirst()
        .map(
            base -> {
              Ingredient effective = effectiveById.getOrDefault(base.id(), base);
              double lower = base.amount() * 0.70;
              double upper = base.amount() * 1.30;
              double proposed = clamp(round(effective.amount() * multiplier), lower, upper);
              return new ProposalDraftItem(
                  "ingredient_amount:" + base.id(),
                  "ingredient_amount",
                  base.name() + " " + actionLabel,
                  base.id(),
                  null,
                  effective.amount(),
                  proposed,
                  null,
                  null);
            })
        .filter(item -> !same(item.beforeAmount(), item.proposedAmount()));
  }

  private java.util.Optional<ProposalDraftItem> stepChange(
      List<RecipeStep> baseSteps,
      Map<UUID, RecipeStep> effectiveById,
      UUID stepId,
      int delta) {
    if (stepId == null) {
      return java.util.Optional.empty();
    }
    return baseSteps.stream()
        .filter(step -> step.id().equals(stepId))
        .filter(RecipeStep::timerAdjustable)
        .filter(step -> "normal".equals(step.safetyTier()))
        .filter(step -> step.cautionNote() == null)
        .filter(step -> step.timerSeconds() != null)
        .findFirst()
        .map(
            base -> {
              RecipeStep effective = effectiveById.getOrDefault(base.id(), base);
              int lower = base.timerMinSeconds() == null ? Math.max(15, base.timerSeconds() - 120) : base.timerMinSeconds();
              int upper = base.timerMaxSeconds() == null ? base.timerSeconds() + 120 : base.timerMaxSeconds();
              int proposed = Math.max(lower, Math.min(upper, effective.timerSeconds() + delta));
              return new ProposalDraftItem(
                  "step_timer:" + base.id(),
                  "step_timer",
                  "'" + compact(base.instruction()) + "' 30초 줄이기",
                  null,
                  base.id(),
                  null,
                  null,
                  effective.timerSeconds(),
                  proposed);
            })
        .filter(item -> !item.beforeSeconds().equals(item.proposedSeconds()));
  }

  private Map<UUID, Ingredient> byIngredientId(List<Ingredient> ingredients) {
    Map<UUID, Ingredient> result = new LinkedHashMap<>();
    ingredients.forEach(item -> result.put(item.id(), item));
    return result;
  }

  private Map<UUID, RecipeStep> byStepId(List<RecipeStep> steps) {
    Map<UUID, RecipeStep> result = new LinkedHashMap<>();
    steps.forEach(item -> result.put(item.id(), item));
    return result;
  }

  private double round(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().doubleValue();
  }

  private double clamp(double value, double lower, double upper) {
    return round(Math.max(lower, Math.min(upper, value)));
  }

  private boolean same(Double left, Double right) {
    return BigDecimal.valueOf(left).compareTo(BigDecimal.valueOf(right)) == 0;
  }

  private String compact(String instruction) {
    String value = instruction.replace("하세요.", "").replace("습니다.", "");
    return value.length() <= 28 ? value : value.substring(0, 28) + "...";
  }
}

record ProposalDraftItem(
    String itemKey,
    String itemType,
    String label,
    UUID ingredientId,
    UUID stepId,
    Double beforeAmount,
    Double proposedAmount,
    Integer beforeSeconds,
    Integer proposedSeconds) {}
