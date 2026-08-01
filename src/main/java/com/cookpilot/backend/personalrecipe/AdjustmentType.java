package com.cookpilot.backend.personalrecipe;

/**
 * diff 행의 종류. DB 의 CHECK (adjustment_type IN ('ADD','MODIFY','REMOVE'))와 짝을 이룬다.
 *
 * ADD    — 원본에 없는 재료/단계를 새로 넣는다. 원본 참조 없이 자기 데이터를 통째로 가진다.
 * MODIFY — 원본 행의 일부 값을 덮어쓴다. 필드 생략은 원본 값 유지.
 *          재료 amount 는 명시적 JSON null 로 값 제거도 표현한다.
 * REMOVE — 원본 행을 이 버전에서 제외한다.
 */
public enum AdjustmentType {
	ADD, MODIFY, REMOVE
}
