# 08. CookPilot MVP API (backend)

작성 기준: 2026-07-13 (2026-07-21 갱신: 조리 세션 서버 API 폐기).
AI 파트 미확정 상태의 MVP API.

## 확정 사항

| 항목 | 결정 |
|---|---|
| 데이터 저장 | repository 계층 미확정 → 서비스 내 인메모리 저장 (재시작 시 초기화) |
| PostgreSQL | 드라이버 의존성 + `db` 프로파일 datasource 설정만 준비 (전부 환경변수 주입). `data-jpa` starter 추가 전까지 실제 연결은 맺지 않음 |
| 인증 | 없음. 고정 목유저 1명 (`00000000-...-0001`, demo@cookpilot.app) |
| API 컨벤션 | `/api/v1` prefix, 순수 DTO JSON, 에러는 RFC7807 ProblemDetail |
| 조리 세션 | 서버에 두지 않는다. 단계 이동/타이머/이벤트는 프론트가 로컬에서 관리하고, 조리가 끝나면 결과(리뷰)만 서버로 넘긴다 |
| 타이머 | 클라이언트 로컬 진행. 서버 기록 없음 |
| AI (STT/TTS/LLM) | 미확정. `ai-feedback` 엔드포인트는 `mock: true` 고정 목데이터 반환 |
| 피드백 → 조정값 구조화 | AI 미확정. 개인 버전의 `adjustmentPayload`는 `source: MOCK` 목데이터 |

## 패키지 구조 (기능별 집합)

```
com.cookpilot.backend
├── recipe/          레시피 조회 (F-01)
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
- `GET /api/v1/recipes` — 목록 + 내 버전 배지 (`hasPersonalVersion`, `latestPersonalVersionId`), 대표 이미지 `imageUrl`
- `GET /api/v1/recipes/{recipeId}` — 상세 (재료, 단계, 단계별 timerSeconds, 레시피/단계별 `imageUrl`)

`imageUrl`은 외부 스토리지 URL이며 이미지가 없으면 `null`.

시드 레시피: 라면 `10000000-0000-0000-0000-000000000001`, 김치볶음밥 `10000000-0000-0000-0000-000000000002`

### review
조리 진행은 프론트 담당이라 세션 ID가 없다. 조리 1회의 기록 = 리뷰이며 `recipeId`를 body로 넘긴다.

- `POST /api/v1/reviews` — body: `{recipeId, rating(1~5), comment?, nextTimeNote?}` → 201.
  저장 시 개인 버전 자동 생성 (`createdPersonalVersionId`)
- `GET /api/v1/reviews/{reviewId}`
- `GET /api/v1/recipes/{recipeId}/reviews` — 최신순

### personalrecipe
- `GET /api/v1/recipes/{recipeId}/personal-versions`
- `GET /api/v1/personal-versions/{versionId}`

### ai (미확정 - 목데이터)
- `POST /api/v1/ai-feedback` — body: `{recipeId, stepIndex, userSpeech}` →
  docs/06 §9 구조(`speechText, screenText, suggestedAction, eventPayload`) + `mock: true`.
  현재 단계는 프론트가 들고 있으므로 요청으로 받는다. 서버에 이벤트를 남기지 않는다.

## 에러

- 404: 없는 레시피/단계/리뷰/버전 (`NotFoundException`)
- 400: 필수값 누락(`recipeId`, `rating`, `userSpeech`), 범위 밖 rating

## 실행/테스트

```bash
./gradlew test        # MockMvc 통합 테스트 + Testcontainers 영속 테스트(Docker 필요)
./gradlew bootRun     # DB 없이 실행
./gradlew bootRun --args='--spring.profiles.active=db'  # PostgreSQL 연결 시
docker compose up --build  # 앱 + PostgreSQL 컨테이너 기동
```
