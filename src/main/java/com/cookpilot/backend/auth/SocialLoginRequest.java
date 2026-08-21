package com.cookpilot.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 소셜 로그인 요청.
 *
 * @param token       제공자에게서 받은 토큰(구글·애플은 ID 토큰, 카카오는 액세스 토큰)
 * @param displayName 선택. 제공자가 토큰에 이름을 싣지 않을 때(애플) 클라이언트가 받은 이름을 넘긴다.
 *                    <b>계정을 처음 만들 때만</b> 쓰이고, 토큰에 이름이 있으면 무시된다 — 검증된
 *                    신원(토큰)이 항상 우선이고, 이 값은 표시용 기본값일 뿐이다.
 */
public record SocialLoginRequest(
		@NotBlank(message = "토큰은 필수입니다.") String token,
		@Size(max = 50, message = "이름은 50자 이내여야 합니다.") String displayName) {
}
