# feat/apple-login — 애플 로그인 (Sign in with Apple) 서버 검증

## 무엇을, 왜

`POST /api/v1/auth/apple` 을 연다. iOS 앱이 다른 소셜 로그인(구글·카카오)을 제공하면 애플 로그인도
**앱 심사 요건**이라, iOS 출시 전에 서버가 먼저 받을 수 있어야 한다.
`docs/feat-social-login.md` 의 "알려진 약점 — 애플 로그인 미포함"을 해소한다.

## 인증 흐름

```
iOS 앱: Sign in with Apple → identity token(JWT) + (최초 1회) 이름
  → POST /auth/apple {"token": "...", "displayName": "홍길동"(선택)}
  → 서버가 애플 공개키(JWKS https://appleid.apple.com/auth/keys)로 서명·iss·aud·exp 검증
  → (provider=APPLE, sub) 로 계정 찾기/만들기 → 우리 세션 토큰
```

구글과 같은 OIDC ID 토큰 방식이다. 카카오처럼 제공자 API 를 되묻지 않는다.

## 핵심 설계 결정

1. **구글 검증기와 골격을 공유한다 — `IdTokenVerifier`.** 서명(JWKS)·발급자·대상(aud)·만료·sub 검사가
   구글과 글자 하나 빼고 같았다. 두 번째 구현이 생기는 시점이라 공통 추상 클래스로 올리고,
   `GoogleVerifier`·`AppleVerifier` 는 상수(JWKS 주소·iss)와 이름 클레임 해석만 가진다.
   구글 동작은 바뀌지 않는다 — 오류 문구의 제공자 이름만 파라미터가 됐다.
2. **aud 는 `APPLE_CLIENT_IDS` 로 나열.** iOS 네이티브 로그인이면 앱 번들 ID(`kr.cooklog.app` — 프론트 PR #68 에서
   Flutter 기본값 `com.cookpilot.cookpilot` 을 콘솔에 등록된 이 값으로 맞췄다),
   안드로이드·웹(리다이렉트 방식)이면 Services ID 가 aud 로 찍힌다. 구글과 같은 이유로 여러 개를 허용한다.
   비어 있으면 `ProviderNotConfiguredException` → 500 (설정 누락은 서버 잘못이지 클라이언트 잘못이 아니다).
3. **이름은 요청 본문 `displayName` 으로 받는다.** 애플은 이름을 토큰에 싣지 않고 최초 로그인 1회만
   클라이언트에 준다. 그래서 `SocialLoginRequest` 에 선택 필드를 추가했다. 규칙:
   - 토큰에 이름이 있는 제공자(구글·카카오)는 **무시**한다 — 검증된 신원이 항상 우선.
   - 계정을 **처음 만들 때만** 쓴다(`findOrCreate` 의 INSERT 경로). 두 번째 로그인에 다른 이름을 보내도
     기존 계정은 바뀌지 않는다.
   - 표시 이름은 어차피 사용자가 고르는 값이라 클라이언트가 보내도 보안상 문제가 없다. 길이만 50자로 막는다.
4. **이메일 가리기(private relay)는 그대로 저장한다.** `xxx@privaterelay.appleid.com` 도 고유하고 메일이
   전달되므로 특별 취급하지 않는다. 계정 식별은 원래 이메일이 아니라 `(provider, sub)` 다.
5. **쉼표 뒤 공백을 다듬는다.** `.env` 에 `a, b` 처럼 쓰면 기존 코드는 ` b` 를 그대로 비교해 조용히 실패했다.
   `strip()` 한 줄 추가 — 구글에도 같이 적용된다.

## API

| 엔드포인트 | 변경 |
| --- | --- |
| `POST /api/v1/auth/{provider}` | `provider` 에 `apple` 추가. body `displayName`(선택, ≤50자) 추가 |

`docs/openapi.json` 은 `OpenApiDocsTest` 로 재생성했다(`SocialLoginRequest.displayName` 추가가 전부).

## 설정

| env | 설명 |
| --- | --- |
| `APPLE_CLIENT_IDS` | 쉼표 구분. iOS 는 번들 ID, 안드로이드·웹은 Services ID. 비우면 애플 로그인이 거부된다 |

`docker-compose.prod.yml`·`.env.example` 에 추가했다. 운영 `.env` 에는 `APPLE_CLIENT_IDS=kr.cooklog.app` 을 넣었다
(2026-08-21, Apple Developer 콘솔 Team `Y9VWJ3KA86` 의 App ID `kr.cooklog.app` 에 Sign In with Apple 활성화 완료).

## 검증

- `IdTokenVerifierTest` — 로컬 HTTP 서버가 테스트용 RSA 공개키(JWKS)를 내주고 같은 키로 서명한 토큰을
  넣는다. Spring·Docker 없이 실제 검증 경로(서명·iss·aud·exp·sub·다중 클라이언트 ID·미설정)를 전부 탄다.
  구글도 같은 방식으로 두 가지 iss 표기와 `name` 클레임을 확인한다.
- `AuthApiTest` — `/auth/apple` 미설정 시 500 이고 환경변수 이름이 응답에 안 드러나는지, `displayName` 51자는 400 인지.

## 알려진 약점·후속

- **탈퇴 시 토큰 revoke 가 없다.** 애플 심사 가이드(5.1.1(v))는 계정 삭제 시 `POST /auth/revoke` 를 요구한다.
  이건 Developer 키(.p8)로 만든 client_secret 과, 로그인 시 받은 `authorizationCode` 를 `/auth/token` 으로
  교환해 둔 refresh token 이 있어야 한다 — 지금은 둘 다 없다. `SocialUnlinker` 구현(`AppleUnlinker`)과
  로그인 시 code 교환을 함께 붙이는 별도 PR 로 한다. iOS 심사 제출 전까지는 필요하다.
- **nonce 검증 없음.** 구글과 동일하게 토큰 재사용(replay) 창은 토큰 만료(애플 10분)로만 막는다.
- 안드로이드 애플 로그인은 Services ID + 리다이렉트 콜백 엔드포인트가 필요해 범위 밖이다. 프론트도 iOS 에서만
  버튼을 보인다.
