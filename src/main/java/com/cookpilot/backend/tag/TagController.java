package com.cookpilot.backend.tag;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 태그 사전. 홈 화면의 칩이 이걸 읽는다.
 *
 * 로그인이 필요 없다 — 사전은 개인 데이터가 아니고, 게스트도 카테고리를 눌러 볼 수 있어야 한다
 * (레시피 목록을 게스트에 연 #76 과 같은 이유다).
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

	private final TagService tagService;

	public TagController(TagService tagService) {
		this.tagService = tagService;
	}

	@GetMapping
	public List<Tag> list() {
		return tagService.findActive();
	}
}
