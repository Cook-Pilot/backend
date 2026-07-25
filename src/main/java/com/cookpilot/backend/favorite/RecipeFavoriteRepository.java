package com.cookpilot.backend.favorite;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeFavoriteRepository extends JpaRepository<RecipeFavoriteEntity, UUID> {

	List<RecipeFavoriteEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

	List<RecipeFavoriteEntity> findByUserIdAndRecipeIdIn(UUID userId, Collection<UUID> recipeIds);

	Optional<RecipeFavoriteEntity> findByUserIdAndRecipeId(UUID userId, UUID recipeId);

	long deleteByUserIdAndRecipeId(UUID userId, UUID recipeId);
}
