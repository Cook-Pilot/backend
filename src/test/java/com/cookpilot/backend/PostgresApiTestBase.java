package com.cookpilot.backend;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.cookpilot.backend.auth.JwtService;
import com.cookpilot.backend.user.UserEntity;
import com.cookpilot.backend.user.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * db 프로파일 API 테스트 공용 베이스.
 *
 * 싱글턴 postgres 컨테이너를 모든 하위 클래스가 공유한다(@Container 대신 수동 start —
 * 클래스마다 컨테이너를 새로 띄우지 않게. 정리는 Testcontainers Ryuk이 한다).
 * 동일 프로퍼티라 Spring 컨텍스트도 캐시로 공유되어 Flyway는 한 번만 돈다.
 *
 * 주의: 컨텍스트/DB 를 공유하므로 테스트 데이터가 클래스 사이에 남는다.
 * 개수 단언은 관대하게(greaterThanOrEqualTo), 레시피는 클래스별로 나눠 쓴다
 * (ReviewFlowApiTest=김치볶음밥, PersonalRecipeDeriveTest=라면).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("db")
@Import(PostgresApiTestBase.MockMvcDefaults.class)
public abstract class PostgresApiTestBase {

	protected static final UUID DEMO_USER_ID =
			UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@BeforeEach
	void setDemoUserRequestContext() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, bearerFor(DEMO_USER_ID));
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	/** 특정 사용자로 요청을 보낼 때 쓰는 Authorization 헤더 값. */
	protected String bearerFor(UUID userId) {
		return "Bearer " + jwtService.issue(userId).token();
	}

	/** 데모 사용자와 구분되는 별도 계정. 소유자 격리를 확인할 때 쓴다. */
	protected UUID createTestUser() {
		return userRepository.saveAndFlush(UserEntity.ofSocial(
				"DEV", "test-" + UUID.randomUUID(), null, "테스트 사용자")).getId();
	}

	@AfterEach
	void clearDemoUserRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@TestConfiguration
	static class MockMvcDefaults {

		@Bean
		MockMvcBuilderCustomizer cookPilotSessionToken(JwtService jwtService) {
			String bearer = "Bearer " + jwtService.issue(DEMO_USER_ID).token();
			return builder -> builder.defaultRequest(get("/")
					.header(HttpHeaders.AUTHORIZATION, bearer));
		}
	}
}
