package com.cookpilot.backend.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * 로그인 결과. 클라이언트는 token 을 안전한 저장소에 넣고 이후 요청의
 * {@code Authorization: Bearer <token>} 에 실어 보낸다.
 */
public record AuthResponse(String token, Instant expiresAt, UUID userId, String displayName) {
}
