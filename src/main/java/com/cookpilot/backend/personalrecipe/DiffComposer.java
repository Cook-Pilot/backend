package com.cookpilot.backend.personalrecipe;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 원본 레시피 + diff → 완성된 개인 레시피 합성기(순수 함수, DB 무관).
 *
 * diff 는 항상 원본 기준 누적이므로 입력은 "원본 목록 + 해당 버전의 diff 목록" 뿐이다.
 * 부모 버전 체인을 재생하지 않는다.
 */
final class DiffComposer {

	private DiffComposer() {
	}

	/** 합성 입력용 원본 재료(엔티티/인메모리 어느 쪽에서든 만들 수 있는 값 타입). */
	record OriginalIngredient(UUID id, String name, BigDecimal amount, String unit,
			boolean required, int sortOrder) {
	}

	/** 합성 입력용 원본 단계. */
	record OriginalStep(UUID id, int stepIndex, String instruction, Integer timerSeconds,
			String cautionNote) {
	}

	/**
	 * 재료 합성: 원본을 sortOrder 순으로 돌며 REMOVE 는 빼고 MODIFY 는 non-null 필드만
	 * 덮어쓴 뒤, ADD 를 sortOrder 순으로 뒤에 붙인다.
	 */
	static List<ComposedIngredient> composeIngredients(List<OriginalIngredient> originals,
			List<IngredientAdjustment> adjustments) {
		Map<UUID, IngredientAdjustment> byOriginal = new HashMap<>();
		List<IngredientAdjustment> adds = new ArrayList<>();
		for (IngredientAdjustment adj : adjustments) {
			if (adj.type() == AdjustmentType.ADD) {
				adds.add(adj);
			} else {
				byOriginal.put(adj.originalIngredientId(), adj);
			}
		}

		List<ComposedIngredient> result = new ArrayList<>();
		originals.stream()
				.sorted(Comparator.comparingInt(OriginalIngredient::sortOrder))
				.forEach(original -> {
					IngredientAdjustment adj = byOriginal.get(original.id());
					if (adj == null) {
						result.add(new ComposedIngredient(original.id(), original.name(),
								original.amount(), original.unit(), original.required(),
								ComposedIngredient.Origin.ORIGINAL));
					} else if (adj.type() == AdjustmentType.MODIFY) {
						result.add(new ComposedIngredient(
								original.id(),
								adj.name() != null ? adj.name() : original.name(),
								adj.amount() != null ? adj.amount() : original.amount(),
								adj.unit() != null ? adj.unit() : original.unit(),
								adj.required() != null ? adj.required() : original.required(),
								ComposedIngredient.Origin.MODIFIED));
					}
					// REMOVE: 결과에서 제외
				});

		adds.stream()
				.sorted(Comparator.comparingInt(IngredientAdjustment::sortOrder))
				.forEach(adj -> result.add(new ComposedIngredient(null, adj.name(), adj.amount(),
						adj.unit(), adj.required() != null ? adj.required() : true,
						ComposedIngredient.Origin.ADDED)));
		return List.copyOf(result);
	}

	/**
	 * 단계 합성: 앵커(-1 = 맨 앞) 뒤에 ADD 를 끼워넣고, 원본은 REMOVE 제외/MODIFY 오버라이드.
	 * 원본 최대 인덱스보다 큰 앵커의 ADD 는 맨 뒤에 붙는다. stepIndex 는 0부터 재부여.
	 */
	static List<ComposedStep> composeSteps(List<OriginalStep> originals,
			List<StepAdjustment> adjustments) {
		Map<UUID, StepAdjustment> byOriginal = new HashMap<>();
		Map<Integer, List<StepAdjustment>> addsByAnchor = new HashMap<>();
		for (StepAdjustment adj : adjustments) {
			if (adj.type() == AdjustmentType.ADD) {
				addsByAnchor.computeIfAbsent(adj.insertAfterStepIndex(), k -> new ArrayList<>()).add(adj);
			} else {
				byOriginal.put(adj.originalStepId(), adj);
			}
		}
		addsByAnchor.values().forEach(list -> list.sort(Comparator.comparingInt(StepAdjustment::sortOrder)));

		List<OriginalStep> sorted = originals.stream()
				.sorted(Comparator.comparingInt(OriginalStep::stepIndex))
				.toList();
		int maxOriginalIndex = sorted.isEmpty() ? -1 : sorted.get(sorted.size() - 1).stepIndex();

		List<ComposedStep> result = new ArrayList<>();
		appendAdds(result, addsByAnchor.remove(-1));

		for (OriginalStep original : sorted) {
			StepAdjustment adj = byOriginal.get(original.id());
			if (adj == null) {
				result.add(new ComposedStep(0, original.id(), original.instruction(),
						original.timerSeconds(), original.cautionNote(), ComposedStep.Origin.ORIGINAL));
			} else if (adj.type() == AdjustmentType.MODIFY) {
				result.add(new ComposedStep(0, original.id(),
						adj.instruction() != null ? adj.instruction() : original.instruction(),
						adj.timerSeconds() != null ? adj.timerSeconds() : original.timerSeconds(),
						adj.cautionNote() != null ? adj.cautionNote() : original.cautionNote(),
						ComposedStep.Origin.MODIFIED));
			}
			// REMOVE 여도 그 위치 뒤에 앵커된 ADD 는 살아있다(앵커는 원본 인덱스 기준).
			appendAdds(result, addsByAnchor.remove(original.stepIndex()));
		}

		// 원본 범위를 벗어난 앵커(예: 원본이 3개인데 anchor=10)는 맨 뒤에 앵커 순으로 붙인다.
		addsByAnchor.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(entry -> appendAdds(result, entry.getValue()));

		List<ComposedStep> reindexed = new ArrayList<>(result.size());
		for (int i = 0; i < result.size(); i++) {
			ComposedStep s = result.get(i);
			reindexed.add(new ComposedStep(i, s.originalStepId(), s.instruction(), s.timerSeconds(),
					s.cautionNote(), s.origin()));
		}
		return List.copyOf(reindexed);
	}

	private static void appendAdds(List<ComposedStep> result, List<StepAdjustment> adds) {
		if (adds == null) {
			return;
		}
		for (StepAdjustment adj : adds) {
			result.add(new ComposedStep(0, null, adj.instruction(), adj.timerSeconds(),
					adj.cautionNote(), ComposedStep.Origin.ADDED));
		}
	}
}
