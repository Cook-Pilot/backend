package com.cookpilot.backend.tag;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

	private final TagRepository tagRepository;

	public TagService(TagRepository tagRepository) {
		this.tagRepository = tagRepository;
	}

	/**
	 * 화면에 낼 태그.
	 *
	 * 비활성(is_active=false)은 내리지 않는다 — 분류를 돌린 뒤 건수가 적어 내린 태그가
	 * 칩으로 남아 있으면, 눌렀을 때 한 화면도 못 채운다(V14 가 미리 적어 둔 실패 방식이다).
	 */
	@Transactional(readOnly = true)
	public List<Tag> findActive() {
		return tagRepository.findByActiveTrueOrderBySortOrderAscCodeAsc().stream()
				.map(tag -> new Tag(tag.getCode(), tag.getLabelKo()))
				.toList();
	}
}
