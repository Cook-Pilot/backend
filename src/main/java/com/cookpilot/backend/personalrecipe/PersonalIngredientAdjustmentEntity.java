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
import jakarta.persistence.Table;

/**
 * personal_ingredient_adjustments 테이블 매핑.
 *
 * 개인 버전 1개가 가진 재료 diff 행. diff 는 항상 원본 레시피 기준 누적이므로
 * 렌더링은 원본 재료 + 이 행들만으로 끝난다.
 * ADD 는 originalIngredientId 없이 자기 데이터를 가지고, MODIFY/REMOVE 는 원본을 가리킨다
 * (조합은 DB CHECK 로 강제).
 */
@Entity
@Table(name = "personal_ingredient_adjustments")
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

	@Column(name = "name")
	private String name;

	@Column(name = "amount")
	private BigDecimal amount;

	@Column(name = "unit")
	private String unit;

	@Column(name = "is_required")
	private Boolean required;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder = 0;

	protected PersonalIngredientAdjustmentEntity() {
	}

	public PersonalIngredientAdjustmentEntity(UUID personalVersionId, UUID originalIngredientId,
			AdjustmentType adjustmentType, String name, BigDecimal amount, String unit,
			Boolean required, int sortOrder) {
		this.personalVersionId = personalVersionId;
		this.originalIngredientId = originalIngredientId;
		this.adjustmentType = adjustmentType;
		this.name = name;
		this.amount = amount;
		this.unit = unit;
		this.required = required;
		this.sortOrder = sortOrder;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPersonalVersionId() {
		return personalVersionId;
	}

	public UUID getOriginalIngredientId() {
		return originalIngredientId;
	}

	public AdjustmentType getAdjustmentType() {
		return adjustmentType;
	}

	public String getName() {
		return name;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getUnit() {
		return unit;
	}

	public Boolean getRequired() {
		return required;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
