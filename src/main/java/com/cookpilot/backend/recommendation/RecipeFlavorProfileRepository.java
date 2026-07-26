package com.cookpilot.backend.recommendation;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeFlavorProfileRepository
		extends JpaRepository<RecipeFlavorProfileEntity, UUID> {
}
