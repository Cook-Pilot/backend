package com.cookpilot.backend.review;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCookReviewRepository extends JpaRepository<PostCookReviewEntity, UUID> {

	List<PostCookReviewEntity> findByRecipeIdOrderByCreatedAtDesc(UUID recipeId);
}
