# CookPilot API

CookPilot의 조리 세션, 음성 이벤트, 예외 코칭, 조리 후 개인화를 담당하는 Spring Boot API입니다.

기본 설정은 별도 계정 없이 실행되는 H2 파일 DB와 안전 규칙 기반 코치를 사용합니다. `GEMINI_API_KEY`를 설정하면 안전 규칙으로 처리되지 않는 조리 질문만 Gemini로 전달합니다.

## 로컬 실행

필수 환경:

- Java 21
- Maven 3.9 이상 또는 포함된 Maven Wrapper

```bash
./mvnw spring-boot:run
```

API는 `http://localhost:8080`, 로컬 데이터는 `data/`에 저장됩니다.

선택 설정:

```bash
export GEMINI_API_KEY='<gemini-api-key>'
export GEMINI_MODEL='gemini-2.5-flash'
./mvnw spring-boot:run
```

## Supabase PostgreSQL

```bash
export SPRING_PROFILES_ACTIVE=postgres
export SUPABASE_DB_URL='jdbc:postgresql://<host>:5432/postgres?sslmode=require'
export SUPABASE_DB_USER='postgres'
export SUPABASE_DB_PASSWORD='<password>'
./mvnw spring-boot:run
```

`postgres` 프로필은 Flyway로 스키마와 MVP 레시피 3개를 생성하도록 구성돼 있습니다. 현재 자동 테스트는 H2와 H2의 PostgreSQL 호환 모드까지이며, 실제 PostgreSQL/Supabase migration과 row-lock 경쟁은 배포 전 별도 검증이 필요합니다.

## 테스트

```bash
./mvnw test
```

## 설치 인증과 멱등성

첫 실행에서 설치 식별자를 등록하고 받은 opaque token을 이후 요청의 Bearer credential로 사용합니다. 서버에는 token 원문이 아닌 hash만 저장됩니다.

```http
POST /api/anonymous-installs
Content-Type: application/json

{"installId":"<uuid>"}
```

`/api/health`와 bootstrap을 제외한 API에는 다음 헤더가 필요합니다.

```http
Authorization: Bearer <installToken>
```

세션 생성·완료·중단·후기/skip·제안 승인/거절·rollback 같은 권위 mutation에는 요청마다 생성한 UUID를 재시도 동안 그대로 사용합니다. event/transcript/AI feedback은 이 공통 멱등 응답 저장소의 대상이 아닙니다.

```http
Idempotency-Key: <uuid>
```

## 주요 API

- `GET /api/health`
- `POST /api/anonymous-installs`
- `GET /api/recipes`
- `GET /api/recipes/{recipeId}`
- `POST /api/cook-sessions`
- `GET /api/cook-sessions/active`
- `GET /api/cook-sessions/reviewable`
- `GET /api/cook-sessions/{sessionId}`
- `POST /api/cook-sessions/{sessionId}/events`
- `POST /api/cook-sessions/{sessionId}/transcripts`
- `POST /api/cook-sessions/{sessionId}/complete`
- `POST /api/cook-sessions/{sessionId}/abort`
- `POST /api/cook-sessions/{sessionId}/review`
- `POST /api/cook-sessions/{sessionId}/review-skip`
- `POST /api/ai/feedback`
- `GET /api/personal-recipe-proposals`
- `POST /api/personal-recipe-proposals/{proposalId}/approve`
- `POST /api/personal-recipe-proposals/{proposalId}/reject`
- `POST /api/personal-recipes/{recipeId}/default-version/rollback`

진행 중 세션 조회는 생성 시 고정한 재료·단계 snapshot을 함께 반환합니다. 정확한 현재 단계·타이머 재개는 이 snapshot과 프런트엔드의 durable journal을 함께 사용합니다. 후기는 완료된 세션에 한 번만 제출할 수 있고, 제안된 변경은 명시적 승인 전까지 기본 개인 버전을 바꾸지 않습니다.

설치 token은 계정 로그인이 아닌 같은 설치 범위의 MVP 격리 수단입니다. rate limit, token 회전·복구, bootstrap 응답 유실 복구와 운영 CORS allowlist는 외부 배포 전에 추가해야 합니다.
