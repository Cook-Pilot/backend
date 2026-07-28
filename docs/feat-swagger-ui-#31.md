# feat/swagger-ui-#31 — Swagger UI(springdoc-openapi) API 문서화 도입

이슈: #31

## 무엇을 왜

엔드포인트가 레시피/리뷰/즐겨찾기/홈/추천/피드백으로 늘면서 Flutter 클라이언트와의 API 계약을
README 수기 나열로 공유하기 어려워졌다. springdoc-openapi 를 붙여 코드에서 OpenAPI 스펙을
자동 생성하고 Swagger UI 로 노출한다.

- 스펙: `GET /v3/api-docs` (JSON)
- UI: `GET /swagger-ui.html` → `/swagger-ui/index.html` 로 리다이렉트

## 핵심 설계 결정과 근거

1. **springdoc 3.0.3 명시 고정.** 3.x 라인이 Spring Boot 4 / Framework 7 호환이고 2.x 는
   Boot 3 전용이다(올리면 기동 실패). Boot BOM 이 springdoc 버전을 관리하지 않으므로
   `build.gradle` 에 버전을 직접 적었다.
2. **어노테이션 도배 없이 자동 스캔으로 시작.** 컨트롤러/record DTO/`ProblemDetail` 은
   springdoc 이 그대로 읽는다. `@Operation`/`@Schema` 는 자동 생성이 부족한 곳이 실제로
   생기면 그때 추가한다.
3. **메타데이터만 `OpenApiConfig` 빈으로 정의.** title/version/설명 외에는 손대지 않는다.
4. **접근 제한 없음.** 인증 없는 MVP 라 UI 를 그대로 노출한다. 인증 도입 시 재검토(이슈 #31 메모).

## 스키마/API 변경

- DB 스키마 변경 없음(마이그레이션 없음).
- 신규 라우트는 springdoc 이 제공하는 `/v3/api-docs*`, `/swagger-ui*` 뿐. `/api/v1` 계약 변경 없음.

## 검증

- `OpenApiDocsTest` (Docker 불필요, 기본 프로파일 + 테스트 h2 컨텍스트):
  스펙 200 + `info.title` + `paths` 비어있지 않음, UI 진입점 리다이렉트 확인.
- 스펙 내용 단언은 의도적으로 안 한다 — 컨트롤러 추가마다 깨지는 단언은 유지비만 든다.

## 알려진 약점·후속

- `./gradlew bootRun`(무프로파일) 이 datasource 부재로 기동 실패하는 문제는 **이 브랜치와 무관하게
  main 에서도 재현**된다(JPA 도입 이후 h2 가 testRuntimeOnly 라 메인 런타임에 임베디드 DB 없음).
  CLAUDE.md 의 "run, no DB" 서술과 어긋남 — 별도 이슈로 다룰 것.
- 운영 노출 정책(스웨거 UI 를 prod 에서 끌지 여부)은 인증 도입 시점에 결정.
