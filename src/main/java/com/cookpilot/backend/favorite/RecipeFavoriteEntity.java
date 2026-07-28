package com.cookpilot.backend.favorite;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Table(
		name = "recipe_favorites",
		uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "recipe_id"})
)
@Getter
public class RecipeFavoriteEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RecipeFavoriteEntity() {
	}

	public RecipeFavoriteEntity(UUID userId, UUID recipeId) {
		this.userId = userId;
		this.recipeId = recipeId;
	}
}
