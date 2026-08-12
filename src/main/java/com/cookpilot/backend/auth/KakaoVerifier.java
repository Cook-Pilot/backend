package com.cookpilot.backend.auth;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로그인. 구글·애플과 달리 ID 토큰(JWT)이 아니라 <b>액세스 토큰</b>을 받아
 * 카카오 API 에 사용자 정보를 물어보는 방식이다(토큰이 유효해야만 응답이 온다 = 검증).
 *
 * 이메일은 없을 수 있다 — 카카오는 이메일 제공이 선택 동의다.
 */
@Component
public class KakaoVerifier implements SocialVerifier {

	private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

	private final RestClient restClient;

	public KakaoVerifier() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		// 로그인 요청이 카카오 지연에 물려 톰캣 스레드를 잡아먹지 않게 짧게 끊는다.
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(4));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	@Override
	public String provider() {
		return "KAKAO";
	}

	@Override
	@SuppressWarnings("unchecked")
	public SocialIdentity verify(String accessToken) {
		Map<String, Object> body;
		try {
			body = restClient.get()
					.uri(USER_INFO_URL)
					.header("Authorization", "Bearer " + accessToken)
					.retrieve()
					.body(Map.class);
		} catch (Exception exception) {
			// 401(토큰 무효)도 여기로 온다 — 클라이언트 잘못이므로 401 로 내린다.
			throw new InvalidTokenException("카카오 토큰을 검증하지 못했습니다.");
		}
		if (body == null || body.get("id") == null) {
			throw new InvalidTokenException("카카오 사용자 정보를 읽지 못했습니다.");
		}

		String providerUserId = String.valueOf(body.get("id"));
		String email = null;
		String displayName = null;
		if (body.get("kakao_account") instanceof Map<?, ?> account) {
			Object rawEmail = account.get("email");
			email = rawEmail == null ? null : String.valueOf(rawEmail);
			if (account.get("profile") instanceof Map<?, ?> profile && profile.get("nickname") != null) {
				displayName = String.valueOf(profile.get("nickname"));
			}
		}
		return new SocialIdentity(provider(), providerUserId, email, displayName);
	}
}
