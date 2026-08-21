package com.cookpilot.backend.tag;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 레시피에 붙은 태그. 지금은 **필터 질의에서만** 쓴다.
 *
 * 부여는 마이그레이션(V21)이 하고 앱은 아직 태그를 쓰거나 지우지 않는다. 그래서
 * 부여 출처(assigned_by)·신뢰도·모델 같은 열은 매핑하지 않았다 — JPA 는 매핑한 열만
 * 검증하므로(ddl-auto: validate) 필요해질 때 더하면 된다.
 */
@Entity
@Table(name = "recipe_tags")
@IdClass(RecipeTagEntity.Key.class)
@Getter
public class RecipeTagEntity {

	@Id
	@Column(name = "recipe_id")
	private UUID recipeId;

	@Id
	@Column(name = "tag_code")
	private String tagCode;

	@Column(name = "axis_code", nullable = false)
	private String axisCode;

	protected RecipeTagEntity() {
	}

	/** 복합 PK (recipe_id, tag_code). */
	public static class Key implements Serializable {

		private UUID recipeId;
		private String tagCode;

		protected Key() {
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Key key)) {
				return false;
			}
			return java.util.Objects.equals(recipeId, key.recipeId)
					&& java.util.Objects.equals(tagCode, key.tagCode);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(recipeId, tagCode);
		}
	}
}
