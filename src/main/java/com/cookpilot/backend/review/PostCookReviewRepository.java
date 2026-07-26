package com.cookpilot.backend.review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostCookReviewRepository extends JpaRepository<PostCookReviewEntity, UUID> {

	List<PostCookReviewEntity> findByRecipeIdOrderByCreatedAtDesc(UUID recipeId);

	List<PostCookReviewEntity> findByUserIdAndRecipeIdOrderByCreatedAtDesc(
			UUID userId, UUID recipeId);

	Optional<PostCookReviewEntity> findByIdAndUserId(UUID id, UUID userId);

	Optional<PostCookReviewEntity> findByUserIdAndClientSessionId(UUID userId, UUID clientSessionId);

	List<PostCookReviewEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

	List<PostCookReviewEntity> findByUserIdAndCookedAtGreaterThanEqualAndCookedAtLessThanOrderByCookedAtDesc(
			UUID userId, Instant from, Instant to);

	@Query(value = """
			SELECT latest.*
			FROM (
				SELECT DISTINCT ON (review.recipe_id) review.*
				FROM post_cook_reviews review
				JOIN recipes recipe ON recipe.id = review.recipe_id
				WHERE review.user_id = :userId
				  AND recipe.status = 'active'
				ORDER BY review.recipe_id, review.created_at DESC, review.id DESC
			) latest
			ORDER BY latest.created_at DESC, latest.id DESC
			LIMIT :limit
			""", nativeQuery = true)
	List<PostCookReviewEntity> findRecentDistinctActiveByUserId(
			@Param("userId") UUID userId,
			@Param("limit") int limit);
}
