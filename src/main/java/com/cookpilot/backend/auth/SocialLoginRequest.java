package com.cookpilot.backend.auth;

import jakarta.validation.constraints.NotBlank;

/** 제공자에게서 받은 토큰(구글·애플은 ID 토큰, 카카오는 액세스 토큰). */
public record SocialLoginRequest(@NotBlank(message = "토큰은 필수입니다.") String token) {
}
