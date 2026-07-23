package com.cookpilot.backend.personalrecipe;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalIngredientAdjustmentRepository
		extends JpaRepository<PersonalIngredientAdjustmentEntity, UUID> {

	List<PersonalIngredientAdjustmentEntity> findByPersonalVersionIdOrderBySortOrderAsc(UUID personalVersionId);
}
