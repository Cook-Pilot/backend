package com.cookpilot.backend.ai;

import java.util.UUID;

/**
 * 조리 중 AI 피드백 요청. 조리 컨텍스트(어떤 레시피의 몇 번째 단계인지)는 서버에 세션이 없어
 * 프론트가 들고 있으므로 요청으로 받는다.
 *
 * userSpeech 는 <b>클라이언트가 STT 로 변환해 보낸 텍스트</b>다. 서버는 음성을 받지 않는다.
 * 오디오를 서버가 직접 받아 STT 하는 방향으로 바뀌면 이 요청은 JSON 이 아니라 multipart 가 되고,
 * 이 레코드도 그때 함께 바뀐다. 지금 텍스트 하나만 받는 것은 그 전제를 고정한 결과다.
 */
public record AiFeedbackRequest(UUID recipeId, Integer stepIndex, String userSpeech) {
}
