package com.cookpilot.backend.pantry;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PantryIngredientCatalogRepository
		extends JpaRepository<PantryIngredientCatalogEntity, String> {

	List<PantryIngredientCatalogEntity> findAllByOrderBySortOrderAsc();
}
