package com.cookpilot.backend.recipe;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * recipe_ingredients 테이블 매핑(그룹 A). recipe_id FK로 레시피에 속한다.
 */
@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredientEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	@Column(name = "name", nullable = false)
	private String name;

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

	public RecipeIngredientEntity(UUID recipeId, String name, BigDecimal amount, String unit,
			boolean required, int sortOrder) {
		this.recipeId = recipeId;
		this.name = name;
		this.amount = amount;
		this.unit = unit;
		this.required = required;
		this.sortOrder = sortOrder;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRecipeId() {
		return recipeId;
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

	public boolean isRequired() {
		return required;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
