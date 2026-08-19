package com.cookpilot.backend.auth;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 카카오 계정 연결 해제. 카카오 개발자 정책이 회원 탈퇴 시 unlink 호출을 요구한다
 * (admin key 경유 {@code POST /v1/user/unlink}).
 *
 * best-effort 다 — 카카오 장애가 우리 탈퇴를 막으면 안 되므로(방침 제9조는 우리 쪽 삭제를
 * 약속한다) 실패는 삼키고 로그만 남긴다. 미해제 건은 로그로 찾아 수동 처리한다.
 *
 * admin key 가 설정되지 않으면 호출 없이 건너뛴다 — 로컬·CI 는 카카오 없이 돌아야 한다.
 */
@Component
public class KakaoUnlinker {

	private static final Logger log = LoggerFactory.getLogger(KakaoUnlinker.class);

	private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";

	private final RestClient restClient;
	private final String adminKey;

	public KakaoUnlinker(@Value("${cookpilot.auth.kakao.admin-key:}") String adminKey) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		// KakaoVerifier 와 같은 이유 — 카카오 지연이 톰캣 스레드를 잡아먹지 않게 짧게 끊는다.
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(4));
		this.restClient = RestClient.builder().requestFactory(factory).build();
		this.adminKey = adminKey;
	}

	public void unlink(String providerUserId) {
		if (!StringUtils.hasText(adminKey)) {
			log.warn("카카오 admin key 미설정 — unlink 건너뜀: providerUserId={}", providerUserId);
			return;
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("target_id_type", "user_id");
		form.add("target_id", providerUserId);
		try {
			restClient.post()
					.uri(UNLINK_URL)
					.header("Authorization", "KakaoAK " + adminKey)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.toBodilessEntity();
		} catch (Exception exception) {
			log.error("카카오 unlink 실패 — 수동 해제 필요: providerUserId={}", providerUserId, exception);
		}
	}
}
