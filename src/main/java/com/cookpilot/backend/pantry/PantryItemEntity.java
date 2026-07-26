package com.cookpilot.backend.pantry;

import java.time.Instant;
import java.time.LocalDate;
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
 * pantry_items 테이블 매핑(그룹 A). 사용자가 담은 재료와 소진 예정일.
 * 같은 재료를 다시 담으면 소진 예정일만 갱신한다(user_id, ingredient_name UNIQUE).
 */
@Entity
@Table(name = "pantry_items")
public class PantryItemEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "ingredient_name", nullable = false)
	private String ingredientName;

	@Column(name = "use_by_date", nullable = false)
	private LocalDate useByDate;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PantryItemEntity() {
	}

	public PantryItemEntity(UUID userId, String ingredientName, LocalDate useByDate) {
		this.userId = userId;
		this.ingredientName = ingredientName;
		this.useByDate = useByDate;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getIngredientName() {
		return ingredientName;
	}

	public LocalDate getUseByDate() {
		return useByDate;
	}

	public void setUseByDate(LocalDate useByDate) {
		this.useByDate = useByDate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
