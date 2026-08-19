package com.cookpilot.backend.recipe;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * recipe_ingredients 테이블 매핑(그룹 A). recipe_id FK로 레시피에 속한다.
 * 재료 이름은 ingredients 마스터 FK 로만 참조한다(#70) — 레시피별 값(양/단위/순서)만 여기 있다.
 */
@Entity
@Table(name = "recipe_ingredients")
@Getter
public class RecipeIngredientEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	@ManyToOne(optional = false)
	@JoinColumn(name = "ingredient_id", nullable = false)
	private IngredientEntity ingredient;

	@Column(name = "amount")
	private BigDecimal amount;

	@Column(name = "unit")
	private String unit;

	@Column(name = "is_required", nullable = false)
	private boolean required = true;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder = 0;

	protected RecipeIngredientEntity() {
	}

	public RecipeIngredientEntity(UUID recipeId, IngredientEntity ingredient, BigDecimal amount,
			String unit, boolean required, int sortOrder) {
		this.recipeId = recipeId;
		this.ingredient = ingredient;
		this.amount = amount;
		this.unit = unit;
		this.required = required;
		this.sortOrder = sortOrder;
	}

	public String getName() {
		return ingredient.getName();
	}
}
