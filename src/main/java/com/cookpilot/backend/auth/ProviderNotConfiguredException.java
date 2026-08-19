package com.cookpilot.backend.auth;

/**
 * 제공자 설정(클라이언트 ID 등)이 서버에 없다. 클라이언트 잘못이 아니므로 500 이고,
 * 어떤 설정이 비었는지는 로그에만 남긴다 — 서버 구성 상태를 밖으로 흘리지 않는다.
 */
public class ProviderNotConfiguredException extends RuntimeException {

	public ProviderNotConfiguredException(String message) {
		super(message);
	}
}
