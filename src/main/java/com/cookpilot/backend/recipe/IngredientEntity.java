package com.cookpilot.backend.recipe;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * ingredients 마스터 테이블 매핑(#70). 재료 이름은 여기에만 1행 존재하고
 * recipe_ingredients / personal_ingredient_adjustments 가 FK 로 참조한다.
 */
@Entity
@Table(name = "ingredients")
@Getter
public class IngredientEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "name", nullable = false, unique = true)
	private String name;

	protected IngredientEntity() {
	}

	public IngredientEntity(String name) {
		this.name = name;
	}
}
