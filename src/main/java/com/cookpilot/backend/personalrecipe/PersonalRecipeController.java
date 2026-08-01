package com.cookpilot.backend.personalrecipe;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PersonalRecipeController {

	private final PersonalRecipeService personalRecipeService;

	public PersonalRecipeController(PersonalRecipeService personalRecipeService) {
		this.personalRecipeService = personalRecipeService;
	}

	@GetMapping("/recipes/{recipeId}/personal-versions")
	public List<PersonalRecipeVersion> listByRecipe(@PathVariable UUID recipeId) {
		return personalRecipeService.findByRecipe(recipeId);
	}

	/**
	 * 한 번의 조리에서 나온 수정 사항으로 개인 버전을 만든다.
	 *
	 * 조리 1회가 만드는 것은 리뷰(POST /reviews)와 개인 버전 둘이고, 둘은 별개 요청이다.
	 * 리뷰는 "무엇을 조리했고 어땠는지"의 기록이고 여기는 "레시피가 어떻게 달라졌는지"다.
	 * 리뷰가 먼저 저장되고 경로가 그 리뷰를 지목한다 — 리뷰가 clientSessionId 로 멱등하므로
	 * 재시도해도 reviewId 가 바뀌지 않고, 그 안정된 id 가 여기서도 멱등키가 된다.
	 * 같은 reviewId 로 두 번 부르면 처음 만든 버전을 그대로 201 로 돌려준다. 재시도한 쪽이
	 * 원하는 것은 "버전이 있다"이지 "누가 만들었나"가 아니라 히트를 200 으로 가르지 않는다.
	 * 레시피·인분·부모 버전은 본문에 없다 — 그 리뷰 행에서 읽는다.
	 *
	 * 수정이 결과적으로 원본과 같으면 버전을 만들지 않는다 → 204. 무엇이 수정으로
	 * 인정되는지(각 층의 판정)는 서비스가 소유한다. 검증도 전부 서비스에 있다.
	 */
	@PostMapping("/reviews/{reviewId}/personal-versions")
	public ResponseEntity<PersonalRecipeVersion> create(
			@PathVariable UUID reviewId, @RequestBody RecipeEditRequest request) {
		return personalRecipeService.createFromEdits(reviewId, request)
				.map(version -> ResponseEntity.status(HttpStatus.CREATED).body(version))
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/** 상세: 메타 + 합성된 재료/단계(그대로 렌더링 가능) + 원시 diff(무엇이 바뀌었는지). */
	@GetMapping("/personal-versions/{versionId}")
	public PersonalRecipeVersionDetail get(@PathVariable UUID versionId) {
		return personalRecipeService.findDetailById(versionId);
	}
}
