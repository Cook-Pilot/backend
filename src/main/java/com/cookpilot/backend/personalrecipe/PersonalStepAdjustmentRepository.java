package com.cookpilot.backend.personalrecipe;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalStepAdjustmentRepository
		extends JpaRepository<PersonalStepAdjustmentEntity, UUID> {

	List<PersonalStepAdjustmentEntity> findByPersonalVersionIdOrderBySortOrderAsc(UUID personalVersionId);
}
