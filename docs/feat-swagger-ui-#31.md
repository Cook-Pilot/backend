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
5. **스펙을 파일로 커밋한다(`docs/openapi.json`).** springdoc 스펙은 런타임에만 존재하는데,
   Flutter 클라이언트 코드 생성과 스펙 diff 는 git 에 박힌 파일을 필요로 한다.
6. **덤프는 gradle 플러그인이 아니라 테스트에서 한다.** `springdoc-openapi-gradle-plugin` 은
   `bootRun` 으로 앱을 실제 기동해 `/v3/api-docs` 를 긁는데, 이 프로젝트의 무프로파일 기동은
   datasource 부재로 실패한다(아래 약점 참고) → CI 에 postgres 서비스를 붙여야 한다.
   `OpenApiDocsTest` 의 MockMvc 컨텍스트는 테스트 h2 로 뜨므로 Docker 없이 동일한 스펙을 얻는다.
7. **덤프 시 키 정렬 + 들여쓰기로 정규화.** 스캔 순서가 흔들려도 바이트가 같아야 CI diff 가
   헛실패하지 않는다. 정규화를 산출물 쪽에 넣은 덕에 CI 는 `jq`/python 없이 `diff` 만 쓴다.
   (로컬 2회 연속 실행 바이트 동일 확인.)

## 스키마/API 변경

- DB 스키마 변경 없음(마이그레이션 없음).
- 신규 라우트는 springdoc 이 제공하는 `/v3/api-docs*`, `/swagger-ui*` 뿐. `/api/v1` 계약 변경 없음.
- 신규 산출물 `docs/openapi.json` — 현재 17 paths / 27 schemas.

## 스펙 파일 갱신 방법

컨트롤러나 DTO 를 바꿨으면 스펙도 같이 커밋해야 CI 가 통과한다.

```bash
./gradlew test --tests '*OpenApiDocsTest' && cp build/openapi.json docs/openapi.json
```

CI(`ci.yml` 의 `Check OpenAPI spec is up to date`)가 `docs/openapi.json` 과 방금 생성한
`build/openapi.json` 을 `diff` 해서 어긋나면 실패시킨다. 봇 자동 커밋 대신 사람이 커밋하는
방식을 골랐다 — PR 에 봇 커밋이 끼지 않고 워크플로 권한도 `contents: read` 로 유지된다.

**주의: PR 에서 CI 는 브랜치 HEAD 가 아니라 main 과 합친 머지 커밋을 검사한다**
(`pull_request` 이벤트의 `actions/checkout` 기본 동작). 그래서 내가 API 를 안 건드렸어도
main 에 새 엔드포인트가 들어오면 이 검사가 실패한다 — 브랜치의 스펙에는 그 엔드포인트가 없기 때문.
해결은 main 을 브랜치에 합치고 위 명령으로 재생성하는 것. 즉 이 게이트는 "스펙 최신성" 과 함께
"PR 이 main 과 동기화됨" 도 사실상 강제한다. 생성물을 커밋하는 방식의 구조적 비용이다.

## 검증

- `OpenApiDocsTest` (Docker 불필요, 기본 프로파일 + 테스트 h2 컨텍스트):
  스펙 200 + `info.title` + `paths` 비어있지 않음, UI 진입점 리다이렉트 확인.
- 스펙 내용 단언은 의도적으로 안 한다 — 컨트롤러 추가마다 깨지는 단언은 유지비만 든다.
  대신 `docs/openapi.json` diff 가 그 역할을 한다: 스펙이 바뀌면 PR 에 변경분이 드러난다.
- `docker compose up --build` 로 실제 기동해 `/actuator/health` UP, `/v3/api-docs` 200,
  `/swagger-ui.html` 200 확인.

## 알려진 약점·후속

- `./gradlew bootRun`(무프로파일) 이 datasource 부재로 기동 실패하는 문제는 **이 브랜치와 무관하게
  main 에서도 재현**된다(JPA 도입 이후 h2 가 testRuntimeOnly 라 메인 런타임에 임베디드 DB 없음).
  CLAUDE.md 의 "run, no DB" 서술과 어긋남 — 별도 이슈로 다룰 것.
- 운영 노출 정책(스웨거 UI 를 prod 에서 끌지 여부)은 인증 도입 시점에 결정.
- 스펙 덤프가 테스트의 부수효과다(단언이 아니라 파일 생성). 앱 무프로파일 기동이 고쳐지면
  `springdoc-openapi-gradle-plugin` 으로 옮기는 편이 정석이다.
- breaking change 게이트(`oasdiff` 로 main 스펙과 비교해 필드 삭제·타입 변경 시 실패)는
  넣지 않았다. 프론트가 실제로 스펙을 소비하기 시작한 뒤 붙인다.
- Flutter 클라이언트 코드 생성(`openapi-generator` dart-dio 등)은 클라이언트 레포 몫.
