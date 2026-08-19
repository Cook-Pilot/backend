# refactor/remove-anonymous-auth — 익명 발급 제거 (소셜 로그인 3단계)

## 무엇을 왜

지금까지 신원은 **클라이언트가 헤더에 적어 보낸 UUID**(`X-CookPilot-User-Id`)였다. 그 UUID 를 아는 사람은 누구나 그 계정으로 행세할 수 있었고, 발급 API(`POST /users/anonymous`)는 무인증이라 계정을 무한히 찍어낼 수 있었다.

소셜 로그인(#56)과 프론트 전환(#51)이 끝나 **모든 클라이언트가 세션 토큰을 보내므로**, 전환기 동안 함께 살려 뒀던 익명 경로를 걷어낸다. 이 PR 이후 신원은 오직 서버가 서명한 JWT 에서만 나온다.

## 핵심 결정

**1. 헤더 폴백을 남기지 않는다.** 하나라도 남으면 그 경로가 곧 우회로다. `UserService.currentUserId()` 는 `Authorization: Bearer` 만 본다 — 없으면 401(`USER_SESSION_REQUIRED`), 형식이 틀리면 401(`INVALID_TOKEN`).

**2. 익명 사용자 행은 지우지 않는다.** 후기·개인 버전이 FK 로 물고 있어 지우면 그 기록까지 사라진다. `provider` 가 NULL 이라 **다시 로그인할 수는 없는 과거 데이터**로 남는다. V12 의 CHECK 제약이 "둘 다 NULL 또는 둘 다 NOT NULL" 이라 이 행들은 그대로 유효하다.

**3. 레이트 리밋을 로그인으로 옮긴다.** `signup` 존은 익명 발급을 막으려고 만든 것이었다. 그 엔드포인트가 사라지면서 **무인증 진입점은 `/api/v1/auth/*` 하나만 남으므로**, 규칙을 그쪽으로 옮긴다. 안 옮기면 계정을 무제한 만들어 업로드 제한(10r/m)을 우회할 수 있고, 개발자 로그인 시크릿도 무한히 때려볼 수 있다.

**4. 테스트 인증을 토큰으로 바꾼다.** `PostgresApiTestBase` 가 데모 헤더 대신 `bearerFor(userId)` 로 실제 JWT 를 발급한다. 소유자 격리 테스트가 쓰던 "익명 사용자 두 명 만들기"는 `createTestUser()`(DEV provider + 랜덤 식별자)로 대체했다.

## 스키마/API 변경

| 구분 | 변경 |
| --- | --- |
| API | `POST /api/v1/users/anonymous` **삭제** |
| API | `X-CookPilot-User-Id` / `Idempotency-Key` 헤더 **삭제** (읽지 않는다) |
| DTO | `User` 에서 `betaNumber`, `anonymous` **삭제** |
| 스키마 | `V15` — `users` 에서 `is_anonymous`, `anonymous_installation_id`, `beta_number` 컬럼과 `users_beta_number_seq` 시퀀스 삭제 |
| 인프라 | nginx `signup` 존을 `/api/v1/users/anonymous` → `/api/v1/auth/` 로 이동 |

`docs/openapi.json` 도 함께 갱신했다(경로 1개 + 필드 2개 감소).

## 검증

- 백엔드 전체 175개 통과 (UserApiTest 10→8, 익명 발급 3건 삭제 + 세션 검증 1건 추가. 프로필 온보딩 3건은 인증만 토큰으로 바꿔 그대로 유지)
- V15 는 V1~V14 를 모두 적용한 DB 위에서 Testcontainers 로 실제 실행됨

## 알려진 약점·후속

- **nginx 설정은 머지만으로 반영되지 않는다.** CI 배포는 컨테이너만 바꾼다. 서버에서 `infra/setup.sh` 를 다시 돌리거나 `infra/nginx/cookpilot.conf` 를 직접 설치하고 `nginx -t && systemctl reload nginx` 를 해야 로그인 레이트 리밋이 실제로 걸린다. **그전까지는 무인증 진입점이 무제한이다.**
- **롤백이 안전하지 않다.** V15 가 돈 뒤 이전 이미지로 되돌리면 JPA 가 없는 컬럼을 찾다가 기동에 실패한다. 되돌려야 한다면 컬럼을 되살리는 마이그레이션이 먼저다.
- **배포 순서가 곧 장애 지점이다.** 이 백엔드가 뜨는 순간 구버전 앱은 모든 요청이 401 이 된다. 팀 개발 기기의 앱을 먼저 최신으로 올리고 배포할 것.
- **기존 익명 사용자 16명의 데이터는 되찾을 수 없다.** 행은 남지만 로그인 경로가 없다. 필요하면 배포 전에 해당 행의 `provider`/`provider_user_id` 를 실제 소셜 계정으로 수동 연결해야 한다.
- **동시 로그인 경합 테스트가 없다.** 익명 발급의 멱등성·동시성 테스트를 지우면서 그 자리를 대신할 테스트를 넣지 못했다. `AuthService.insertOrReread()` 가 같은 역할을 하지만, 개발자 로그인은 고정 계정이라 공유 DB 위에서는 경합이 재현되지 않는다.
- **토큰 만료 후 재로그인 흐름은 아직 없다**(백엔드 #66). 지금은 14일이 지나면 앱에서 다시 로그인해야 한다.
