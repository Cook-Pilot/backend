package com.cookpilot.backend.personalrecipe;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import com.cookpilot.backend.recipe.IngredientEntity;

/**
 * personal_ingredient_adjustments 테이블 매핑.
 *
 * 개인 버전 1개가 가진 재료 diff 행. diff 는 항상 원본 레시피 기준 누적이므로
 * 렌더링은 원본 재료 + 이 행들만으로 끝난다.
 * ADD 는 originalIngredientId 없이 자기 데이터를 가지고, MODIFY/REMOVE 는 원본을 가리킨다
 * (조합은 DB CHECK 로 강제).
 * 재료 이름은 ingredients 마스터 FK 로만 참조한다(#70) — ADD 는 필수, MODIFY 는 교체
 * 오버라이드(NULL = 원본 유지), REMOVE 는 안 쓴다.
 */
@Entity
@Table(name = "personal_ingredient_adjustments")
@Getter
public class PersonalIngredientAdjustmentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "personal_version_id", nullable = false)
	private UUID personalVersionId;

	@Column(name = "original_ingredient_id")
	private UUID originalIngredientId;

	@Enumerated(EnumType.STRING)
	@Column(name = "adjustment_type", nullable = false)
	private AdjustmentType adjustmentType;

	@ManyToOne
	@JoinColumn(name = "ingredient_id")
	private IngredientEntity ingredient;

	@Column(name = "amount")
	private BigDecimal amount;

	/** MODIFY amount 키가 있었는지. true + amount NULL 이 명시적 양 제거다. */
	@Column(name = "amount_override_present", nullable = false)
	private boolean amountOverridePresent;

	@Column(name = "unit")
	private String unit;

	// 래퍼 Boolean 이라 Lombok 이 isRequired() 가 아니라 getRequired() 를 만든다(기존과 동일).
	@Column(name = "is_required")
	private Boolean required;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder = 0;

	protected PersonalIngredientAdjustmentEntity() {
	}

	public PersonalIngredientAdjustmentEntity(UUID personalVersionId, UUID originalIngredientId,
			AdjustmentType adjustmentType, IngredientEntity ingredient, BigDecimal amount, String unit,
			Boolean required, int sortOrder) {
		this(personalVersionId, originalIngredientId, adjustmentType, ingredient, amount, unit,
				required, sortOrder,
				adjustmentType == AdjustmentType.MODIFY && amount != null);
	}

	public PersonalIngredientAdjustmentEntity(UUID personalVersionId, UUID originalIngredientId,
			AdjustmentType adjustmentType, IngredientEntity ingredient, BigDecimal amount, String unit,
			Boolean required, int sortOrder, boolean amountOverridePresent) {
		this.personalVersionId = personalVersionId;
		this.originalIngredientId = originalIngredientId;
		this.adjustmentType = adjustmentType;
		this.ingredient = ingredient;
		this.amount = amount;
		this.amountOverridePresent = adjustmentType == AdjustmentType.MODIFY
				&& (amountOverridePresent || amount != null);
		this.unit = unit;
		this.required = required;
		this.sortOrder = sortOrder;
	}

	public String getName() {
		return ingredient == null ? null : ingredient.getName();
	}
}
