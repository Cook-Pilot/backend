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
				SELECT recipeIngredient.id
				FROM RecipeIngredientEntity recipeIngredient
				WHERE recipeIngredient.recipeId = recipe.id
				  AND LOWER(recipeIngredient.ingredient.name) LIKE LOWER(CONCAT('%', :ingredient, '%')) ESCAPE '\\'
			  ))
			  AND (:tagAxisCount = 0 OR :tagAxisCount = (
				SELECT COUNT(DISTINCT recipeTag.axisCode)
				FROM RecipeTagEntity recipeTag
				WHERE recipeTag.recipeId = recipe.id
				  AND recipeTag.tagCode IN :tagCodes
			  ))
			ORDER BY recipe.title ASC, recipe.id ASC
			""", countQuery = """
			SELECT COUNT(recipe.id)
			FROM RecipeEntity recipe
			WHERE recipe.status = :status
			  AND (:title = '' OR LOWER(recipe.title) LIKE LOWER(CONCAT('%', :title, '%')) ESCAPE '\\')
			  AND (:ingredient = '' OR EXISTS (
				SELECT recipeIngredient.id
				FROM RecipeIngredientEntity recipeIngredient
				WHERE recipeIngredient.recipeId = recipe.id
				  AND LOWER(recipeIngredient.ingredient.name) LIKE LOWER(CONCAT('%', :ingredient, '%')) ESCAPE '\\'
			  ))
			  AND (:tagAxisCount = 0 OR :tagAxisCount = (
				SELECT COUNT(DISTINCT recipeTag.axisCode)
				FROM RecipeTagEntity recipeTag
				WHERE recipeTag.recipeId = recipe.id
				  AND recipeTag.tagCode IN :tagCodes
			  ))
			""")
	Page<RecipeEntity> search(
			@Param("status") String status,
			@Param("title") String title,
			@Param("ingredient") String ingredient,
			@Param("tagCodes") java.util.Collection<String> tagCodes,
			@Param("tagAxisCount") long tagAxisCount,
			Pageable pageable);
}
