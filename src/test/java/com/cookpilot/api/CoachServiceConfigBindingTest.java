package com.cookpilot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cookpilot.api.ApiModels.AiFeedbackRequest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * `cookpilot.ai.safety-intercept-enabled`가 실제 Spring 컨텍스트에서 바인딩되는지 확인한다.
 *
 * <p>{@link CoachServiceTest}는 생성자를 직접 호출하므로 프로퍼티 키 이름이 틀려도 통과한다. 키를 잘못 적으면
 * 조용히 기본값(false)으로 떨어져 스위치가 동작하지 않으므로 여기서 컨텍스트를 띄워 검증한다.
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:coach_binding_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.flyway.enabled=true",
      "spring.flyway.locations=classpath:db/h2/migration",
      "spring.sql.init.mode=never",
      "cookpilot.ai.gemini-api-key=",
      "cookpilot.ai.safety-intercept-enabled=true"
    })
class CoachServiceConfigBindingTest {
  @Autowired CoachService coach;
  @MockitoBean CookSessionRepository sessions;
  @MockitoBean GeminiCoach gemini;

  private final UUID sessionId = UUID.randomUUID();

  @BeforeEach
  void stubSession() {
    when(sessions.findContext(any(), any(), anyInt(), any()))
        .thenReturn(
            new SessionContext(sessionId, "토마토 파스타", UUID.randomUUID(), 0, "면을 삶는다", 300, 120));
  }

  @Test
  void settingThePropertyToTrueRestoresTheSafetyIntercept() {
    var response =
        coach.answer(
            new InstallPrincipal(UUID.randomUUID(), null),
            new AiFeedbackRequest(sessionId, "기름에 불이 붙었어", 0, 120));

    assertThat(response.screenText()).contains("뚜껑");
    verify(gemini, never()).answer(any(), any());
  }
}
