package com.cookpilot.backend.auth;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 네이버 로그인. 카카오와 같은 <b>액세스 토큰</b> 방식 — 클라이언트(네이버 SDK·웹 OAuth)가 받은
 * 액세스 토큰을 네이버 회원 프로필 API 에 넣어 본다. 토큰이 유효해야만 응답이 온다 = 검증.
 *
 * 응답 형태는 {@code {"resultcode":"00","message":"success","response":{"id":..,"email":..,"nickname":..}}}.
 * 계정 식별자는 {@code response.id} 다 — 네이버가 앱마다 다르게 발급하는 고정 값이라 이메일처럼
 * 바뀌지 않는다. 이메일·별명은 제공 동의가 선택이라 없을 수 있다.
 *
 * 서버 쪽 설정이 없다 — 구글의 client-id(aud 검사)에 해당하는 것이 필요 없다. 프로필 API 는
 * 우리 애플리케이션으로 발급된 토큰에만 응답하므로 토큰 자체가 출처 증명이다.
 */
@Component
public class NaverVerifier implements SocialVerifier {

	private static final String USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

	private final RestClient restClient;

	public NaverVerifier() {
		this(RestClient.builder().requestFactory(shortTimeoutFactory()));
	}

	/** 테스트가 {@code MockRestServiceServer} 를 묶은 builder 를 넣는 입구. */
	NaverVerifier(RestClient.Builder builder) {
		this.restClient = builder.build();
	}

	private static SimpleClientHttpRequestFactory shortTimeoutFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		// 로그인 요청이 네이버 지연에 물려 톰캣 스레드를 잡아먹지 않게 짧게 끊는다(카카오와 동일).
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(4));
		return factory;
	}

	@Override
	public AuthProvider provider() {
		return AuthProvider.NAVER;
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
			// 401(토큰 무효·만료)도 여기로 온다 — 클라이언트 잘못이므로 401 로 내린다.
			throw new InvalidTokenException("네이버 토큰을 검증하지 못했습니다.");
		}
		// 네이버는 HTTP 200 이어도 resultcode 로 실패를 알리는 경우가 있어 둘 다 본다.
		if (body == null
				|| !"00".equals(String.valueOf(body.get("resultcode")))
				|| !(body.get("response") instanceof Map<?, ?> profile)
				|| profile.get("id") == null) {
			throw new InvalidTokenException("네이버 사용자 정보를 읽지 못했습니다.");
		}

		String providerUserId = String.valueOf(profile.get("id"));
		String email = profile.get("email") == null ? null : String.valueOf(profile.get("email"));
		// 별명이 없으면 이름으로. 둘 다 없으면 null — 표시명 기본값은 AuthService 가 정한다.
		Object nickname = profile.get("nickname") != null ? profile.get("nickname") : profile.get("name");
		String displayName = nickname == null ? null : String.valueOf(nickname);
		return new SocialIdentity(provider(), providerUserId, email, displayName);
	}
}
