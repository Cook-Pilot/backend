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

@Entity
@Table(name = "recipe_flavor_profiles")
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

	public UUID getRecipeId() {
		return recipeId;
	}

	public String getCuisine() {
		return cuisine;
	}

	public String getDishType() {
		return dishType;
	}

	public List<String> getCookingMethods() {
		return List.copyOf(cookingMethods);
	}

	public List<String> getSauceBases() {
		return List.copyOf(sauceBases);
	}

	public List<String> getFlavorTags() {
		return List.copyOf(flavorTags);
	}

	public int getProfileVersion() {
		return profileVersion;
	}

	public String getSource() {
		return source;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
