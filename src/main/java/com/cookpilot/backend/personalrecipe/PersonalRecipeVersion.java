package com.cookpilot.backend.personalrecipe;

import java.time.Instant;
import java.util.UUID;

/**
 * 개인 레시피 버전 메타 DTO(목록/생성 응답용).
 * 조정 내용과 합성 결과는 {@link PersonalRecipeVersionDetail} 로 제공한다.
 */
public record PersonalRecipeVersion(
		UUID id,
		UUID userId,
		UUID recipeId,
		int versionNumber,
		String title,
		String summary,
		UUID sourceReviewId,
		UUID parentVersionId,
		boolean isDefault,
		Instant createdAt
) {

	static PersonalRecipeVersion from(PersonalRecipeVersionEntity entity) {
		return new PersonalRecipeVersion(
				entity.getId(),
				entity.getUserId(),
				entity.getRecipeId(),
				entity.getVersionNumber(),
				entity.getTitle(),
				entity.getSummary(),
				entity.getSourceReviewId(),
				entity.getParentVersionId(),
				entity.isDefault(),
				entity.getCreatedAt()
		);
	}
}
