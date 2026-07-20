package com.cookpilot.backend.recipe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * recipes 테이블 매핑. 기본 레시피 메타(그룹 A).
 * 재료/단계는 recipe_id FK로 별도 엔티티(RecipeIngredientEntity, RecipeStepEntity)에 둔다.
 */
@Entity
@Table(name = "recipes")
public class RecipeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "description")
	private String description;

	@Column(name = "base_servings", nullable = false)
	private BigDecimal baseServings = BigDecimal.ONE;

	@Column(name = "status", nullable = false)
	private String status = "active";

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected RecipeEntity() {
	}

	public RecipeEntity(String title, String description, BigDecimal baseServings) {
		this.title = title;
		this.description = description;
		if (baseServings != null) {
			this.baseServings = baseServings;
		}
	}

	public UUID getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigDecimal getBaseServings() {
		return baseServings;
	}

	public void setBaseServings(BigDecimal baseServings) {
		this.baseServings = baseServings;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
