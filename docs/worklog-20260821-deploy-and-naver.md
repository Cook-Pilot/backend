# 워크로그 — 배포 경로 복구와 네이버 로그인 (2026-08-21)

"PR 열린 거 싹 다 알려줘"에서 시작해 하루 동안 네 갈래가 이어졌다. 열린 PR 정리 → 계정 삭제(#81)
충돌 해결·머지 → 네이버 로그인 3개 리포 → **"왜 웹 배포가 안 되나"** 를 파다가 서버와 리포가
양방향으로 어긋난 것을 발견해 되돌려 놓는 것까지.

| | 결과 |
| --- | --- |
| 머지 | backend #87 #88 #89(태그 3부작), #81(계정 삭제); web #4 #2 #3(랜딩·로그인·개명), #5(CI) |
| 새 PR | 네이버 로그인 backend#91 · web#6 · frontend#67 / 배포 web#7 · web#8 · backend#93 |
| 발견 | 운영 로그인 경로에 rate limit 없음(8/20 서버·리포가 반대로 움직임), 서버 Caddy 구성이 리포에 없음, 인증서 볼륨 없음 |
| 정정 | "도메인 안 붙음·포트 충돌 미해결"은 오판 — 서버엔 이미 돼 있었다. 리포만 보고 단정한 것이 원인 |

상세는 각 PR 본문에 있다. 이 문서는 **왜 그렇게 했는지**만 남긴다.

---

## 결정 기록

### 1. #81 — V16 재번호가 아니라 V22, 그리고 P1 은 머지로 닫혔다

**상황.** #81(계정 삭제)이 `CONFLICTING`. 8/19 에 만든 브랜치가 main 보다 9커밋 뒤였고, 마이그레이션
`V16__account_deletion.sql` 이 그 사이 main 에 들어온 `V16__drop_anonymous_user_columns.sql`(#73)과 번호가
겹쳤다. 운영 DB 는 이미 V21 까지 적용된 상태.

**선택.** main 머지 + `V22` 로 개명. 내용은 그대로. Flyway 는 `out-of-order` 가 꺼져 있어 V16 을 지금
넣으면 기동 실패다(#88 때 Claude 리뷰가 지적했던 바로 그 시나리오).

Codex·Claude 가 공통으로 낸 P1(식별자 헤더만으로 남의 계정 삭제 가능)은 **main 의 #73 이 헤더 폴백을
걷어내면서 자연히 닫혔다.** 회귀 테스트(`유효한_세션_토큰_없이는_삭제되지_않는다`)만 추가했다.
Vackam 의 "unlinker 부모 클래스" 제안은 `SocialUnlinker` 인터페이스로 받았다 — 네이버·애플이 곧 붙으므로.

### 2. 네이버 — 구글이 아니라 카카오 방식

네이버는 OIDC ID 토큰을 주지 않는다. 그래서 **액세스 토큰을 서버가 프로필 API 에 되물어 검증**하는
카카오 경로를 그대로 탔다(`NaverVerifier`). 서버 설정(env)이 하나도 필요 없는 이유 — 프로필 API 는 우리
애플리케이션으로 발급된 토큰에만 응답하므로 토큰 자체가 출처 증명이다.

네이버만 다른 점 둘, 둘 다 테스트로 고정했다.
- 웹: **scope 파라미터가 없다**(받을 항목은 개발자센터가 정한다) · **토큰 교환에 인가 때의 `state` 를 함께
  보내야 한다**(`exchangeCode(provider, code, state?)`).
- 앱: 플러그인 `flutter_naver_login` 이 Activity 를 `FlutterFragmentActivity` 로 강제 캐스팅한다 —
  **`MainActivity` 를 바꾸지 않으면 로그인 버튼을 누르기도 전에 앱 시작 시 죽는다.** 플러그인 소스
  `onAttachedToActivity` 에서 확인했고, 디버그 APK 빌드로 검증했다.
- 앱 Client Secret 은 SDK 가 매니페스트에서 읽는 구조라 피할 수 없지만, `android/local.properties`(gitignored)
  에서 gradle 이 주입하게 해 저장소에는 남기지 않았다.

**버튼은 기본적으로 꺼져 있다.** 팀계정 애플리케이션이 아직 없다. 카카오처럼 회원 식별자가
**애플리케이션 단위**라 실사용자 개방 전에 팀계정으로 확정해야 한다.

### 3. 웹 배포가 안 되던 진짜 이유 — 시크릿이 아니라 경로

**상황.** web `deploy` 잡이 매번 빨간 X. 로그는 `DEPLOY_HOST 설정이 없습니다`.

**검토.** 시크릿만 넣으면 되는 줄 알았으나, 그 잡은 **SSH** 로 들어가는데 EC2 보안그룹은 22 번을
관리자 IP 에만 연다(`infra/README.md`). GitHub 러너 IP 는 매번 바뀌므로 **시크릿을 넣어도 영영 못 들어간다.**
backend 는 같은 서버에 **OIDC → IAM 역할 → SSM** 으로 이미 잘 배포하고 있었다.

**선택.** web 도 같은 길로(web#7). 시크릿이 필요 없고 포트를 열 일도 없다. backend 가 쓰는 변수 3개
(`AWS_DEPLOY_ROLE_ARN`·`AWS_REGION`·`EC2_INSTANCE_ID`)를 web 에도 등록했다.

> 부수 발견: IAM 역할 신뢰 정책이 `repo:Cook-Pilot/backend` 의 **main ref 로만** 허용돼 있다. 임시 브랜치에서
> AssumeRole 을 시도해 `Not authorized` 로 확인. web 을 추가할 때도 `ref:refs/heads/main` 형식으로.

### 4. "도메인 안 붙음·포트 충돌" 오판 — 리포만 보고 서버를 보지 않았다

리포에는 `listen 80` 인 nginx 와 `80:80` 을 잡는 Caddy 가 있어 충돌한다고 적었고, DNS 도 확인하지 않은 채
"안 붙었다"고 했다. 실제로는 **8/20 에 서버에서 전부 해결돼 있었다** — `cooklog.kr` → Caddy(80/443,
Let's Encrypt) → `/api/v1/*` 는 nginx(8081) → Spring. IP 직접 호출도 Caddy 가 받아 넘긴다.

`curl -I https://cooklog.kr` 한 줄이면 알 수 있었다. **원칙: 운영 상태를 말할 때는 운영에 요청을 보내
확인한 뒤 말한다.** 리포는 "의도"이지 "상태"가 아니다.

### 5. 서버 설정을 읽는 법 — SSM 워크플로가 아니라 gzip+base64 한 줄

서버 파일(Caddyfile·compose·nginx)을 리포로 옮기려면 내용이 필요했다. Session Manager 화면은 복사가
어렵고 이 PC 엔 AWS 자격증명이 없다(`~/.aws` 에 셸 명령이 잘못 붙여넣어져 깨져 있음).

- (A) 임시 브랜치에 SSM 읽기 워크플로 → **기각**: 신뢰 정책이 main 한정이라 AssumeRole 거부(3번 참고).
- (B) 출력을 `gzip -9c | base64 -w0` 로 **한 줄**로 만들어 붙여넣기 → **선택**. 577줄이 한 줄이 됐다.
  `.env` 값은 `sed 's/=.*/=X/'` 로 가려서 키 이름만.

### 6. 서버 ↔ 리포 양방향 어긋남 — 어느 쪽이 맞는가

덤프를 한 줄씩 대조했다.

| 파일 | 서버에만 | 리포에만 | 판정 |
| --- | --- | --- | --- |
| web `Caddyfile.preview` | cooklog.kr HTTPS 블록, 리다이렉트, IP 호환 | #2 의 로그인 경로 허용·16KB 제한 | **둘 다 맞다 → 합친다** |
| web compose | caddy `ports`·`extra_hosts` | 소셜 로그인 env | 둘 다 맞다 → 합친다 |
| backend nginx | 8081, real_ip, `$fwd_proto` | `/api/v1/auth/` rate limit(#73) | 둘 다 맞다 → 합친다 |
| backend `ratelimit.conf` | `signup` zone **삭제** | `signup` zone 을 로그인용으로 **재사용** | **리포가 맞다** |

마지막 줄이 문제였다. 8/20 같은 날, 서버에서는 "익명 가입이 사라졌으니 죽은 설정"이라 지웠고 리포에서는
#73 이 그 이름을 로그인 제한에 썼다. 서로 모르고 반대로 간 결과 **운영 로그인 경로에 속도 제한이 없는
채로 하루가 지났다**(개발자 시크릿 무제한 시도 가능). backend#93 에서 zone 을 `login` 으로 **개명**해
되살렸다 — 이름이 용도를 말하면 다시 "죽은 설정"으로 오인되지 않는다.

### 7. 승격(A) 이지 이전(B) 이 아니다

web 운영을 리포 구성에 맞추는 두 길.
- (A) 지금 도는 `cookpilot-web-preview` 스택을 그대로 운영으로 인정하고, 이미지만 로컬 빌드 → GHCR pull 로
- (B) 새 디렉터리에 `docker-compose.prod.yml` 로 새로 띄우고 옛 스택 정지

**선택: (A).** 바뀌는 게 적다. 사전예약 데이터가 든 볼륨(`cookpilot_preview_data`)을 그대로 쓰고, 인증서도
그대로(B 는 재발급 — Let's Encrypt 주 5회 제한), 절체 순간이 없다. 디렉터리 이름이 "preview" 인 어색함은
남지만, compose 프로젝트명·볼륨이 거기서 나오므로 바꾸려면 이관을 같이 계획해야 한다. 나중 일로 미뤘다.

web#8 이 그 결과다. 서버에 없던 것 하나를 더했다 — **Caddy 인증서 볼륨.** 없으면 자동 배포가 컨테이너를
교체할 때마다 재발급을 요청하고, 주 5회를 넘기면 사이트가 안 뜬다.

### 8. nginx 를 남긴다

Caddy 가 `/api/v1/*` 를 직접 8080 으로 보내면 nginx 는 필요 없다. 그래도 남긴 이유는 서버 주석에 적힌
그대로 — **사진 업로드·로그인 rate limit 이 거기 있고 검증돼 있다.** Caddy 로 옮기려면 플러그인이 필요하다.
Caddy 뒤에 서면 `$remote_addr` 가 도커 게이트웨이가 되어 제한이 전 사용자를 한 IP 로 묶어버리므로
`set_real_ip_from 172.16.0.0/12` + `X-Forwarded-For` 신뢰가 함께 가야 한다(8081 은 외부에서 못 닿아 위조 경로 없음).

### 9. 태그 3부작 머지 순서 — 번호가 곧 순서

#87(V20) → #88(V21) → #89(API) 로 머지했다. #88 리뷰가 짚은 대로 `flyway.out-of-order` 가 꺼져 있어
V21 이 V20 보다 먼저 운영에 적용되면 나중 V20 이 거부된다. 순서는 지켜졌고, 이어서 #90(분류기 기록)이
문화권·용도 축을 채워 **태그 없는 레시피 0건**이 됐다.

## 남은 것

1. backend#93 머지 → 서버 `nginx reload`(PR 본문 명령) → 로그인 429 확인
2. web#7 → web#8 머지
3. AWS IAM `cookpilot-github-deploy` 신뢰 정책에 `repo:Cook-Pilot/web:ref:refs/heads/main`
4. 서버 `.env.preview` → `.env` + `SITE_HOST=cooklog.kr` (web#8 본문) → main 푸시 → `deploy`·`smoke` 초록
5. 네이버: 팀계정 애플리케이션 발급 → 웹 env(`NAVER_CLIENT_ID/SECRET`), 앱 `local.properties` + `social_config.dart` 기본값
6. 사전예약 알림 발송 시 `mark-preregistrations-notified.sh` 로 `notified_at` 기록(30일 파기의 시작점)
7. `cooklog.kr` 의 `X-Robots-Tag: noindex` 해제 + HSTS — 공개 차단 관문(#71 #72 #80) 해소 후
8. 이 PC 의 `~/.aws` 설정 복구(현재 깨져 있음) 또는 자격증명 미보유 유지 결정

## 다음에 같은 일을 피하려면

- **서버에서 직접 고친 날은 같은 날 리포 PR 을 연다.** 8/20 의 Caddy·nginx 전환은 훌륭했지만 리포에 없어서
  하루 뒤 "충돌 미해결"로 오인됐고, 로그인 rate limit 이 빠진 채 운영됐다. `infra/README.md` 의 "설정을 바꿀
  때는 이 디렉터리를 고치고 서버에 반영한다"가 그 원칙이다.
- **운영 상태는 운영에 물어본다.** `curl -I`, `ss -ltnp`, `docker ps`. 리포는 의도다.
- 서버 출력을 옮길 땐 `gzip | base64 -w0` 한 줄.
