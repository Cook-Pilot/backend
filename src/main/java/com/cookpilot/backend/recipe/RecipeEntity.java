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
import lombok.Getter;
import lombok.Setter;

/**
 * recipes 테이블 매핑. 기본 레시피 메타(그룹 A).
 * 재료/단계는 recipe_id FK로 별도 엔티티(RecipeIngredientEntity, RecipeStepEntity)에 둔다.
 */
@Entity
@Table(name = "recipes")
@Getter
public class RecipeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Setter
	@Column(name = "title", nullable = false)
	private String title;

	@Setter
	@Column(name = "description")
	private String description;

	@Setter
	@Column(name = "base_servings", nullable = false)
	private BigDecimal baseServings = BigDecimal.ONE;

	@Setter
	@Column(name = "status", nullable = false)
	private String status = "active";

	/** 대표 이미지 URL. 원본은 외부 스토리지에 두고 여기엔 URL만 둔다(NULL = 이미지 없음). */
	@Setter
	@Column(name = "image_url")
	private String imageUrl;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected RecipeEntity() {
	}

	public RecipeEntity(String title, String description, BigDecimal baseServings) {
		this(title, description, baseServings, null);
	}

	public RecipeEntity(String title, String description, BigDecimal baseServings, String imageUrl) {
		this.title = title;
		this.description = description;
		if (baseServings != null) {
			this.baseServings = baseServings;
		}
		this.imageUrl = imageUrl;
	}
}
