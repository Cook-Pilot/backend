package com.cookpilot.backend.personalrecipe;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.recipe.Recipe;
import com.cookpilot.backend.recipe.RecipeService;
import com.cookpilot.backend.user.UserService;

/**
 * repository 계층 미확정. 개인 레시피 버전을 인메모리로 저장한다.
 * 피드백 -> 조정값 구조화는 AI 파트 미확정이라 목데이터 조정값을 사용한다.
 */
@Service
public class PersonalRecipeService {

	private final Map<UUID, PersonalRecipeVersion> versions = new ConcurrentHashMap<>();
	private final RecipeService recipeService;
	private final UserService userService;

	public PersonalRecipeService(RecipeService recipeService, UserService userService) {
		this.recipeService = recipeService;
		this.userService = userService;
	}

	public PersonalRecipeVersion createFromReview(UUID recipeId, UUID sourceReviewId, String comment,
			String nextTimeNote) {
		Recipe recipe = recipeService.findById(recipeId);
		int nextVersionNumber = findByRecipe(recipeId).size() + 1;

		// TODO(AI 미확정): 피드백 코멘트를 실제 조정값(재료/시간/간)으로 구조화하는 부분은 AI 파트 확정 후 교체.
		Map<String, Object> mockAdjustment = Map.of(
				"source", "MOCK",
				"note", "AI 구조화 미확정 - 목데이터 조정값",
				"rawComment", comment == null ? "" : comment,
				"rawNextTimeNote", nextTimeNote == null ? "" : nextTimeNote
		);

		PersonalRecipeVersion version = new PersonalRecipeVersion(
				UUID.randomUUID(),
				userService.getCurrentUser().id(),
				recipeId,
				nextVersionNumber,
				recipe.title() + " - 내 버전 v" + nextVersionNumber,
				comment,
				mockAdjustment,
				sourceReviewId,
				Instant.now()
		);
		versions.put(version.id(), version);
		return version;
	}

	public PersonalRecipeVersion findById(UUID versionId) {
		PersonalRecipeVersion version = versions.get(versionId);
		if (version == null) {
			throw new NotFoundException("개인 레시피 버전을 찾을 수 없습니다: " + versionId);
		}
		return version;
	}

	public List<PersonalRecipeVersion> findByRecipe(UUID recipeId) {
		return versions.values().stream()
				.filter(v -> v.recipeId().equals(recipeId))
				.sorted(Comparator.comparingInt(PersonalRecipeVersion::versionNumber))
				.toList();
	}

	public Optional<PersonalRecipeVersion> findLatestByRecipe(UUID recipeId) {
		return versions.values().stream()
				.filter(v -> v.recipeId().equals(recipeId))
				.max(Comparator.comparingInt(PersonalRecipeVersion::versionNumber));
	}
}
