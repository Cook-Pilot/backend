package com.cookpilot.backend.favorite;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeFavoriteRepository extends JpaRepository<RecipeFavoriteEntity, UUID> {

	List<RecipeFavoriteEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

	List<RecipeFavoriteEntity> findByUserIdAndRecipeIdIn(UUID userId, Collection<UUID> recipeIds);

	Optional<RecipeFavoriteEntity> findByUserIdAndRecipeId(UUID userId, UUID recipeId);

	@Modifying
	@Query(value = """
			INSERT INTO recipe_favorites (id, user_id, recipe_id)
			VALUES (:id, :userId, :recipeId)
			ON CONFLICT (user_id, recipe_id) DO NOTHING
			""", nativeQuery = true)
	int insertIgnore(
			@Param("id") UUID id,
			@Param("userId") UUID userId,
			@Param("recipeId") UUID recipeId);

	long deleteByUserIdAndRecipeId(UUID userId, UUID recipeId);
}
