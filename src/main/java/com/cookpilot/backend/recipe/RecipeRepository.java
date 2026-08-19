package com.cookpilot.backend.recipe;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<RecipeEntity, UUID> {

	List<RecipeEntity> findByStatusOrderByTitleAscIdAsc(String status);

	@Query(value = """
			SELECT recipe
			FROM RecipeEntity recipe
			WHERE recipe.status = :status
			  AND (:title = '' OR LOWER(recipe.title) LIKE LOWER(CONCAT('%', :title, '%')) ESCAPE '\\')
			  AND (:ingredient = '' OR EXISTS (
				SELECT ingredient.id
				FROM RecipeIngredientEntity ingredient
				WHERE ingredient.recipeId = recipe.id
				  AND LOWER(ingredient.name) LIKE LOWER(CONCAT('%', :ingredient, '%')) ESCAPE '\\'
			  ))
			ORDER BY recipe.title ASC, recipe.id ASC
			""", countQuery = """
			SELECT COUNT(recipe.id)
			FROM RecipeEntity recipe
			WHERE recipe.status = :status
			  AND (:title = '' OR LOWER(recipe.title) LIKE LOWER(CONCAT('%', :title, '%')) ESCAPE '\\')
			  AND (:ingredient = '' OR EXISTS (
				SELECT ingredient.id
				FROM RecipeIngredientEntity ingredient
				WHERE ingredient.recipeId = recipe.id
				  AND LOWER(ingredient.name) LIKE LOWER(CONCAT('%', :ingredient, '%')) ESCAPE '\\'
			  ))
			""")
	Page<RecipeEntity> search(
			@Param("status") String status,
			@Param("title") String title,
			@Param("ingredient") String ingredient,
			Pageable pageable);
}
