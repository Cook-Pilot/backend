# 08. CookPilot MVP API (backend)

작성 기준: 2026-07-13. AI 파트 미확정 상태의 MVP API.

## 확정 사항

| 항목 | 결정 |
|---|---|
| 데이터 저장 | repository 계층 미확정 → 서비스 내 인메모리 저장 (재시작 시 초기화) |
| PostgreSQL | 드라이버 의존성 + `db` 프로파일 datasource 설정만 준비 (전부 환경변수 주입). `data-jpa` starter 추가 전까지 실제 연결은 맺지 않음 |
| 인증 | 없음. 고정 목유저 1명 (`00000000-...-0001`, demo@cookpilot.app) |
| API 컨벤션 | `/api/v1` prefix, 순수 DTO JSON, 에러는 RFC7807 ProblemDetail |
| 타이머 | 클라이언트 로컬 진행. 서버는 세션 이벤트로만 기록 |
| AI (STT/TTS/LLM) | 미확정. `ai-feedback` 엔드포인트는 `mock: true` 고정 목데이터 반환 |
| 피드백 → 조정값 구조화 | AI 미확정. 개인 버전의 `adjustmentPayload`는 `source: MOCK` 목데이터 |

## 패키지 구조 (기능별 집합)

```
com.cookpilot.backend
├── recipe/          레시피 조회 (F-01)
├── cooksession/     조리 세션/단계/이벤트 (F-02, F-03, F-04, F-09)
├── review/          조리 후 피드백 (F-10)
├── personalrecipe/  개인 레시피 버전 (F-11, F-12)
├── ai/              LLM 예외 피드백 - 목데이터 (F-08)
├── user/            고정 목유저
└── common/          예외 처리 (ProblemDetail)
```

## 엔드포인트

### user
- `GET /api/v1/users/me` — 고정 목유저

### recipe
- `GET /api/v1/recipes` — 목록 + 내 버전 배지 (`hasPersonalVersion`, `latestPersonalVersionId`)
- `GET /api/v1/recipes/{recipeId}` — 상세 (재료, 단계, 단계별 timerSeconds)

시드 레시피: 라면 `10000000-0000-0000-0000-000000000001`, 김치볶음밥 `10000000-0000-0000-0000-000000000002`

### cooksession
- `POST /api/v1/cook-sessions` — 생성. body: `{recipeId, personalVersionId?}` → 201, 단계 스냅샷 포함, 상태 `COOKING`
- `GET /api/v1/cook-sessions/{id}` — 조회
- `POST /api/v1/cook-sessions/{id}/step` — body: `{direction: NEXT|PREV, source?}` (기본 source: BUTTON)
- `POST /api/v1/cook-sessions/{id}/complete` — 상태 `REVIEW`
- `POST /api/v1/cook-sessions/{id}/abort` — 상태 `ABORTED`
- `POST /api/v1/cook-sessions/{id}/events` — body: `{eventType, stepIndex?, source?, payload?}` → 201
- `GET /api/v1/cook-sessions/{id}/events`

세션 상태: `READY, COOKING, PAUSED, REVIEW, COMPLETED, ABORTED`

### review
- `POST /api/v1/cook-sessions/{id}/review` — body: `{rating(1~5), comment?, nextTimeNote?}` → 201.
  세션이 `REVIEW` 상태여야 함. 저장 시 세션 `COMPLETED` 전환 + 개인 버전 자동 생성 (`createdPersonalVersionId`)
- `GET /api/v1/cook-sessions/{id}/review`

### personalrecipe
- `GET /api/v1/recipes/{recipeId}/personal-versions`
- `GET /api/v1/personal-versions/{versionId}`

### ai (미확정 - 목데이터)
- `POST /api/v1/cook-sessions/{id}/ai-feedback` — body: `{userSpeech}` →
  docs/06 §9 구조(`speechText, screenText, suggestedAction, eventPayload`) + `mock: true`.
  호출 시 `AI_FEEDBACK_REQUESTED` 이벤트 기록.

## 에러

- 404: 없는 레시피/세션/버전 (`NotFoundException`)
- 400: 필수값 누락, 범위 밖 rating, 이동 불가 단계
- 409: 상태 위반 (진행 중 아닌 세션 조작, 완료 전 리뷰, 중복 리뷰)

## 실행/테스트

```bash
./gradlew test        # 22개 테스트 (MockMvc 통합)
./gradlew bootRun     # DB 없이 실행
./gradlew bootRun --args='--spring.profiles.active=db'  # PostgreSQL 연결 시
docker compose up --build  # 앱 + PostgreSQL 컨테이너 기동
```
