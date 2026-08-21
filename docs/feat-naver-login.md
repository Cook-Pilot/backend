# feat/naver-login — 네이버 로그인 (백엔드)

## 무엇을 왜

소셜 로그인 제공자에 **네이버**를 더한다. 국내 사용자 비중이 큰 제공자라 카카오 하나로는 좁다.
백엔드는 `POST /api/v1/auth/naver` 가 열리는 것이 전부다 — 앱(Flutter)·웹(Next.js)의 버튼과
네이버 개발자센터 등록은 별도 PR.

## 어떻게

카카오와 같은 **액세스 토큰 방식**이다. 클라이언트가 네이버 SDK(앱) 또는 OAuth 코드 교환(웹)으로
받은 액세스 토큰을 보내면, 서버가 네이버 회원 프로필 API 에 그 토큰을 넣어 본다.

```
POST /api/v1/auth/naver   { "token": "<네이버 액세스 토큰>" }
서버 → GET https://openapi.naver.com/v1/nid/me  (Authorization: Bearer <토큰>)
     ← { "resultcode":"00", "response": { "id", "email"?, "nickname"?, "name"? } }
     → (NAVER, response.id) 로 계정 조회/생성 → 우리 JWT
```

- **계정 식별자는 `response.id`.** 네이버가 앱마다 다르게 발급하는 고정 값이라 이메일처럼 바뀌지 않는다.
  (카카오 회원번호처럼 **애플리케이션 단위**다 — 개발자센터 애플리케이션을 바꾸면 같은 사람이 다른
  계정이 된다. 실사용자 개방 전에 팀계정 애플리케이션으로 옮겨 둘 것.)
- **이메일·별명은 없을 수 있다.** 제공 동의가 선택 항목이다. 별명이 없으면 이름을 쓴다.
- **HTTP 200 이어도 `resultcode != "00"` 이면 거부.** 네이버는 일부 실패를 200 본문으로 알린다.
- **서버 설정이 없다.** 구글의 `GOOGLE_CLIENT_IDS`(aud 검사)에 해당하는 것이 필요 없다 — 프로필 API 는
  우리 애플리케이션으로 발급된 토큰에만 응답하므로 토큰 자체가 출처 증명이다.
  클라이언트 ID/Secret 은 토큰을 **받는** 쪽(앱·웹)에만 필요하다.

## 변경

- `AuthProvider.NAVER`
- `NaverVerifier implements SocialVerifier` — `AuthService` 는 등록된 검증기 목록을 주입받으므로 분기 코드 변경 없음
- `NaverVerifierTest` — MockRestServiceServer 로 응답 파싱·401·resultcode 실패·id 누락을 고정(Docker 불필요)
- `AuthApiTest` 의 "지원하지 않는 제공자" 예시를 `naver` → `apple` 로

## 하지 않은 것

- **탈퇴 시 네이버 연결 해제.** 카카오와 달리 정책 강제가 아니고, 네이버의 토큰 삭제 API
  (`grant_type=delete`)는 어드민 키가 아니라 **사용자 액세스 토큰**이 필요해 탈퇴 요청에 토큰을
  같이 받아야 한다. 필요해지면 `SocialUnlinker` 구현으로 더한다.
- 앱·웹 버튼, 개발자센터 등록 — 각 리포 PR.

## 클라이언트가 알아야 할 것

| 항목 | 값 |
| --- | --- |
| 엔드포인트 | `POST /api/v1/auth/naver` |
| body | `{"token": "<액세스 토큰>"}` (ID 토큰 아님 — 네이버는 OIDC ID 토큰을 주지 않는다) |
| 응답 | 구글·카카오와 동일 `{token, expiresAt, userId, displayName}` |
| 실패 | 401 `INVALID_TOKEN` (무효·만료), 400 (빈 토큰) |
