package com.cookpilot.backend.recipe;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<IngredientEntity, UUID> {

	Optional<IngredientEntity> findByName(String name);
}
