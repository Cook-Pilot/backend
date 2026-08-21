package com.cookpilot.backend.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 태그 사전. 이 테이블의 행 하나가 화면의 칩 하나다.
 *
 * 읽기 전용이다 — 사전은 마이그레이션으로만 바뀐다(V14·V17·V20). 앱이 태그를 만들지 않는다.
 */
@Entity
@Table(name = "tags")
@Getter
public class TagEntity {

	@Id
	@Column(name = "code")
	private String code;

	@Column(name = "axis_code", nullable = false)
	private String axisCode;

	@Column(name = "label_ko", nullable = false)
	private String labelKo;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	/**
	 * 레시피에 행으로 붙일 수 있는 태그인지. 파생 태그(조회 시점에 계산)는 false 다.
	 * 필터에서는 구분하지 않는다 — 파생 태그로 거르는 것은 아직 만들지 않았다(V17).
	 */
	@Column(name = "is_assignable", nullable = false)
	private boolean assignable;

	protected TagEntity() {
	}
}
