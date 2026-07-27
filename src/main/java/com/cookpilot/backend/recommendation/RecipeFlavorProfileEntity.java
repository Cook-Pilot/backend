package com.cookpilot.backend.recommendation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "recipe_flavor_profiles")
@Getter
public class RecipeFlavorProfileEntity {

	@Id
	@Column(name = "recipe_id")
	private UUID recipeId;

	@Column(name = "cuisine", nullable = false)
	private String cuisine;

	@Column(name = "dish_type", nullable = false)
	private String dishType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "cooking_methods", nullable = false, columnDefinition = "jsonb")
	private List<String> cookingMethods = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "sauce_bases", nullable = false, columnDefinition = "jsonb")
	private List<String> sauceBases = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "flavor_tags", nullable = false, columnDefinition = "jsonb")
	private List<String> flavorTags = new ArrayList<>();

	@Column(name = "profile_version", nullable = false)
	private int profileVersion;

	@Column(name = "source", nullable = false)
	private String source;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected RecipeFlavorProfileEntity() {
	}

	// 아래 JSONB 컬렉션 3개는 내부 리스트를 그대로 넘기지 않도록 방어 복사한다.
	// Lombok 은 직접 정의한 게터가 있으면 생성하지 않는다.

	public List<String> getCookingMethods() {
		return List.copyOf(cookingMethods);
	}

	public List<String> getSauceBases() {
		return List.copyOf(sauceBases);
	}

	public List<String> getFlavorTags() {
		return List.copyOf(flavorTags);
	}
}
