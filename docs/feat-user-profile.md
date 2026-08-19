# feat/user-profile — 성별·연령대 온보딩 수집

## 무엇을 왜

로그인한 유저의 성별·연령대를 수집한다(추천 개인화 근거용). 소셜 제공자(카카오/구글)에서
받지 않고 첫 로그인 후 앱 온보딩 화면에서 직접 묻는다 — 제공자 검수·제3자 제공 이슈를
피하고, 선택 항목으로 받아 개인정보 부담을 최소화한다.

## 핵심 설계 결정

- **선택 항목 + nullable.** 성별·연령대는 서비스 계약 이행에 필수라 보기 어려우므로
  강제하지 않는다. 입력 안 해도 모든 기능 동작.
- **`profile_asked_at` 으로 "물어봤음"을 서버에 기록.** "신규 유저냐"로 분기하면
  온보딩 도중 이탈(다시 물어야 함) / 건너뛴 유저(다시 물으면 안 됨) / 기기 변경을 못
  가른다. `profile_asked_at IS NULL` 하나가 전부 커버 — 신규 유저는 생성 직후 null
  이라 같은 분기에 포함된다. boolean 대신 timestamp 인 이유: 언제 물어봤는지가 남아
  나중에 "오래됐으면 재확인" 정책이 가능하다.
- **건너뛰기도 호출한다.** 클라이언트는 건너뛰기 버튼에서 빈 body `{}` 로 같은 PATCH 를
  조용히 보낸다. 안 보내면 `profile_asked_at` 이 안 찍혀 매 로그인마다 온보딩이 뜬다.
- **연령대는 정수(10~60, 60=60세 이상).** 문자열 enum 대비 정렬·범위 비교가 공짜라
  추천 가중치에 바로 쓸 수 있다. 생년월일 대신 연령대만 받아 최소수집 원칙을 지킨다.
- **성별은 `Gender` enum(M/F/N).** 추천 로직에 들어갈 값이라 문자열 대신 타입으로 고정한다
  (리뷰 반영). 허용 밖 값은 JSON 역직렬화에서 걸려 400. N = 밝히지 않음(선택 안 함).
- 연령대 검증은 서비스에서 허용 집합으로 하고(`IllegalArgumentException` → 400),
  DB `CHECK` 가 최후 방어선.

## 스키마 / API 변경

- `V13__add_user_profile.sql`: `users` 에 `gender TEXT`, `age_group INTEGER`,
  `profile_asked_at TIMESTAMPTZ` (전부 nullable) + CHECK 2개.
- `GET /api/v1/users/me` 응답에 `gender`, `ageGroup`, `profileAskedAt` 추가.
- `PATCH /api/v1/users/me` 신설: body `{gender?, ageGroup?}`.
  - 값 있으면 저장, 빈 body 는 건너뛰기 — 어느 쪽이든 `profile_asked_at = now()`.
  - `gender ∈ {M, F, N}`, `ageGroup ∈ {10,20,30,40,50,60}` 벗어나면 400.

클라이언트 분기: 로그인 → `GET /users/me` → `profileAskedAt == null` 이면 온보딩 표시.

## 알려진 약점·후속

- 이미 저장한 값을 지우는(다시 null 로) API 없음 — PATCH 는 null 필드를 무시한다.
  프로필 수정 화면이 생기면 명시적 삭제 시그널이 필요.
- 개인정보처리방침에 수집 항목·목적 추가 필요(코드 밖 작업).
- 만 14세 미만 이슈: 가입 시 "만 14세 이상" 확인은 클라이언트 몫으로 남아 있음.
