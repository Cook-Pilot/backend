package com.cookpilot.backend.user;

/**
 * 온보딩 프로필 입력. 두 값 모두 선택 — 빈 body({})는 "건너뛰기"로,
 * 저장 없이 물어봤다는 기록만 남긴다.
 */
public record UpdateProfileRequest(String gender, Integer ageGroup) {
}
