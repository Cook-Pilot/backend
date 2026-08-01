package com.cookpilot.backend.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서 기본 정보. 스펙 자체는 springdoc이 컨트롤러/DTO에서 자동 생성하고
 * (/v3/api-docs, /swagger-ui.html), 여기서는 문서 메타데이터만 정의한다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cookPilotOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CookPilot Backend API")
                .description("음성 기반 요리 어시스턴트 CookPilot 백엔드 API. "
                        + "모든 라우트는 /api/v1 아래에 있고, 에러는 RFC-7807 ProblemDetail 형식이다.")
                .version("v1"));
    }
}
