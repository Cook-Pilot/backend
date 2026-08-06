package com.cookpilot.backend.ai;

/**
 * 조리 중 AI 피드백 응답.
 *
 * speechText 는 클라이언트가 TTS 로 그대로 읽는 문장이다.
 * mock 이 true 면 LLM 이 아니라 고정 목데이터다(AI 가 꺼져 있거나 호출에 실패한 경우).
 *
 * 화면 표시 문구·제안 행동(타이머 연장 등)·분석 라벨은 프론트가 앞단에서 처리하기로 해서 뺐다.
 * 필요해지면 그때 필드를 늘린다.
 */
public record AiFeedbackResponse(
		boolean mock,
		String speechText
) {
}
