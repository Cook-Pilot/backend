package com.cookpilot.backend.personalrecipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalRecipeVersionRepository extends JpaRepository<PersonalRecipeVersionEntity, UUID> {

	List<PersonalRecipeVersionEntity> findByUserIdAndRecipeIdOrderByVersionNumberDesc(UUID userId, UUID recipeId);

	Optional<PersonalRecipeVersionEntity> findByUserIdAndRecipeIdAndIsDefaultTrue(UUID userId, UUID recipeId);

	// 진화 계보: 이 버전에서 파생된 자식 버전들.
	List<PersonalRecipeVersionEntity> findByParentVersionId(UUID parentVersionId);
}
