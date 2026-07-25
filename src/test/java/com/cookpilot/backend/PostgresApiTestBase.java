package com.cookpilot.backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.cookpilot.backend.user.UserService;

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

	private static final String DEMO_USER_ID =
			"00000000-0000-0000-0000-000000000001";

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
		request.addHeader(UserService.USER_ID_HEADER, DEMO_USER_ID);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@AfterEach
	void clearDemoUserRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@TestConfiguration
	static class MockMvcDefaults {

		@Bean
		MockMvcBuilderCustomizer cookPilotUserHeader() {
			return builder -> builder.defaultRequest(get("/")
					.header(UserService.USER_ID_HEADER, DEMO_USER_ID));
		}
	}
}
