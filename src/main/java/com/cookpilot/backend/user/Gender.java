package com.cookpilot.backend.user;

/**
 * 성별 코드. V13 의 users_gender_check(M/F/N) 와 1:1 — 값을 늘리면 마이그레이션도 같이 바꾼다.
 * JSON·DB 에는 이름 그대로(M/F/N) 저장된다.
 */
public enum Gender {
	/** 남성 */
	M,
	/** 여성 */
	F,
	/** 밝히지 않음(선택 안 함) */
	N
}
