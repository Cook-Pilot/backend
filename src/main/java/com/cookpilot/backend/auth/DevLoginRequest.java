package com.cookpilot.backend.auth;

import jakarta.validation.constraints.NotBlank;

/** 개발자 로그인. 서버에 설정된 시크릿과 일치해야 한다. */
public record DevLoginRequest(@NotBlank(message = "시크릿은 필수입니다.") String secret) {
}
