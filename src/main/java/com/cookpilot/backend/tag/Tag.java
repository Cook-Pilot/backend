package com.cookpilot.backend.tag;

/**
 * 화면에 나가는 태그.
 *
 * 축(axis)은 내려보내지 않는다. V14 가 정한 대로 화면에서는 '한식 · 반찬 · 안주'가
 * 전부 같은 모양의 칩 한 줄이고, 축은 필터 의미(축 안 OR, 축 사이 AND)를 서버가
 * 계산할 때만 쓴다. 클라이언트가 축을 알면 그 규칙을 클라이언트에도 심게 된다.
 */
public record Tag(String code, String label) {
}
