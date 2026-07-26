package com.cookpilot.backend.pantry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PantryItemRepository extends JpaRepository<PantryItemEntity, UUID> {

	List<PantryItemEntity> findByUserIdOrderByUseByDateAsc(UUID userId);

	Optional<PantryItemEntity> findByUserIdAndIngredientName(UUID userId, String ingredientName);

	Optional<PantryItemEntity> findByIdAndUserId(UUID id, UUID userId);

	void deleteByIdAndUserId(UUID id, UUID userId);
}
