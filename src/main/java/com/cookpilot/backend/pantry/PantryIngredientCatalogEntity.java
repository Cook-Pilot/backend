package com.cookpilot.backend.pantry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * pantry_ingredient_catalog 테이블 매핑(그룹 A). 이모티콘 빠른 담기 UI용 재료 카탈로그.
 * name은 recipe_ingredients.name과 동일한 표기를 써서 보유 재료 매칭에 그대로 쓴다.
 */
@Entity
@Table(name = "pantry_ingredient_catalog")
public class PantryIngredientCatalogEntity {

	@Id
	@Column(name = "name")
	private String name;

	@Column(name = "emoji", nullable = false)
	private String emoji;

	@Column(name = "default_shelf_life_days", nullable = false)
	private int defaultShelfLifeDays;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	protected PantryIngredientCatalogEntity() {
	}

	public String getName() {
		return name;
	}

	public String getEmoji() {
		return emoji;
	}

	public int getDefaultShelfLifeDays() {
		return defaultShelfLifeDays;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
