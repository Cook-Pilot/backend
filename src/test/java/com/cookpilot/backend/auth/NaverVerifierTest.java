package com.cookpilot.backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 네이버 프로필 API 응답을 신원으로 옮기는 규칙. 실제 네이버를 부르지 않고
 * MockRestServiceServer 로 응답을 꾸며 검증한다(Docker·Spring 컨텍스트 불필요).
 */
class NaverVerifierTest {

	private static final String USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

	private MockRestServiceServer server;
	private NaverVerifier verifier;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		verifier = new NaverVerifier(builder);
	}

	@Test
	void 액세스_토큰을_Bearer_로_보내고_id_이메일_별명을_읽는다() {
		server.expect(requestTo(USER_INFO_URL))
				.andExpect(header("Authorization", "Bearer access-token"))
				.andRespond(withSuccess("""
						{"resultcode":"00","message":"success",
						 "response":{"id":"AbCdEf123","email":"cook@naver.com","nickname":"요리왕","name":"홍길동"}}
						""", MediaType.APPLICATION_JSON));

		SocialIdentity identity = verifier.verify("access-token");

		assertThat(identity.provider()).isEqualTo(AuthProvider.NAVER);
		assertThat(identity.providerUserId()).isEqualTo("AbCdEf123");
		assertThat(identity.email()).isEqualTo("cook@naver.com");
		assertThat(identity.displayName()).isEqualTo("요리왕");
		server.verify();
	}

	@Test
	void 별명이_없으면_이름을_쓰고_이메일은_없어도_된다() {
		server.expect(requestTo(USER_INFO_URL))
				.andRespond(withSuccess("""
						{"resultcode":"00","message":"success","response":{"id":"X1","name":"홍길동"}}
						""", MediaType.APPLICATION_JSON));

		SocialIdentity identity = verifier.verify("t");

		assertThat(identity.email()).isNull();
		assertThat(identity.displayName()).isEqualTo("홍길동");
	}

	@Test
	void 토큰이_무효해_401_이면_InvalidTokenException() {
		server.expect(requestTo(USER_INFO_URL))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED)
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"resultcode\":\"024\",\"message\":\"Authentication failed\"}"));

		assertThatThrownBy(() -> verifier.verify("expired"))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void HTTP_200_이어도_resultcode_가_성공이_아니면_거부한다() {
		server.expect(requestTo(USER_INFO_URL))
				.andRespond(withSuccess("""
						{"resultcode":"024","message":"Authentication failed"}
						""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> verifier.verify("t"))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void id_가_없으면_거부한다() {
		server.expect(requestTo(USER_INFO_URL))
				.andRespond(withSuccess("""
						{"resultcode":"00","message":"success","response":{"email":"x@naver.com"}}
						""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> verifier.verify("t"))
				.isInstanceOf(InvalidTokenException.class);
	}
}
