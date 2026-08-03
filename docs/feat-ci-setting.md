# feat/ci-setting — 배포 검증 파이프라인

## 무엇을, 왜

기존 CI는 **`docker push`에서 끝났다.** 이미지를 GHCR에 올리면 워크플로가 초록불이 되고, 그 뒤 VPS의 Watchtower가 최대 5분 뒤 컨테이너를 조용히 교체했다. **새 컨테이너가 실제로 떴는지 확인하는 주체가 없었다.**

이게 위험한 이유는 세 가지가 맞물려서다.

1. `docker-compose.prod.yml`의 `app` 서비스에 헬스체크가 없었다 (`db`에는 있었다). 컨테이너가 기동에 실패해도 `restart: unless-stopped`가 무한히 재시작해서, 겉보기에는 "돌아가는 중"이었다.
2. **마이그레이션은 언제나 빈 DB에서만 검증된다.** `PostgresApiTestBase`는 매번 새 postgres 컨테이너를 띄운다. `SET NOT NULL`(널 있는 컬럼에), 기존 중복 위의 `UNIQUE INDEX` 같은 **데이터 의존 실패**는 CI를 통과하고 운영 기동에서만 터진다.
3. 그래서 나쁜 마이그레이션이 나가면 → 기동 실패 → 무한 재시작 → **CI는 초록, 프로덕션은 정지, 알림 없음.** 알아채는 건 앱을 켠 사용자다.

이 브랜치는 그 구멍을 닫는다. CI가 배포가 실제로 살아났는지까지 확인하고 워크플로를 닫는다.

## 핵심 설계 결정과 근거

### 1. 헬스체크만으로는 부족하다 — 리비전 식별이 필요하다

가장 먼저 떠오르는 구현은 "push 후 `/actuator/health`를 재시도 루프로 찌른다"이다. **이건 가짜 초록이 된다.** push 직후 그 URL에 답하는 건 아직 살아있는 *구* 컨테이너이고, 걔는 당연히 `UP`이다. Watchtower가 2분 뒤 교체하고 새 컨테이너가 죽어도 CI는 이미 초록이 된 뒤다.

그래서 **커밋 SHA를 이미지에 심었다.**

```
ci.yml: build-args GIT_SHA=${{ github.sha }}
  → Dockerfile 런타임 스테이지: ARG GIT_SHA → ENV APP_GIT_SHA
  → application.yml: info.app.commit = ${APP_GIT_SHA:unknown}
  → /actuator/info 로 노출
```

스모크 잡은 `/actuator/info`의 `app.commit`이 `github.sha`와 **일치할 때까지** 기다린 뒤에야 health를 본다. 즉 "교체됐다"와 "정상이다"를 각각 확인한다.

> **불변조건:** 이미지 빌드 방식을 바꾸면 `GIT_SHA` 배선을 반드시 유지해야 한다. 끊어지면 `app.commit`이 `unknown`으로 굳고, 스모크는 조용히 "구 컨테이너가 UP이라고 답했다" 수준으로 퇴화한다 — 실패하지 않고 **영원히 타임아웃**한다(일치하는 SHA를 못 봄). 실패하는 쪽으로 퇴화하는 게 의도다.

### 2. `/actuator/info` 노출 — 노출되는 건 커밋 SHA 하나

`health`는 Boot 기본 노출이지만 `info`는 아니다. `management.endpoints.web.exposure.include: health,info`로 열었다. 공개되는 값은 커밋 SHA 하나뿐이고, 이건 이미지 태그(`type=sha`)로도 이미 드러난다. 비밀이 아니다.

`springdoc.show-actuator`가 기본 false라 **`docs/openapi.json`은 바뀌지 않는다** (CI의 스펙 drift 검사에 영향 없음).

### 3. 배포 주소는 레포에 두지 않는다 — 없으면 실패가 아니라 skip

스모크 잡은 레포 변수 `DEPLOY_BASE_URL`을 읽는다. 없으면 `::warning::` 남기고 `exit 0`. 포크나 초기 세팅에서 CI를 막지 않기 위해서다.

**이 변수를 등록하기 전까지 스모크는 아무 일도 하지 않는다.** 등록 위치: 레포 Settings → Secrets and variables → Actions → **Variables** → `DEPLOY_BASE_URL` = `http://<VPS_IP>:8080`.

### 4. 타임아웃 15분

Watchtower 폴링(`WATCHTOWER_INTERVAL` 기본 300초) + 이미지 pull + Spring 기동을 덮는 값. 15초 간격으로 폴링한다. 이 시간 안에 새 리비전이 `UP`이 되지 않으면 워크플로가 빨갛게 되고, 에러 메시지에 VPS에서 칠 진단 명령을 넣어놨다.

### 5. 헬스체크의 `start_period: 60s`

Spring 기동에 수십 초가 걸린다. `start_period` 동안의 실패는 재시작으로 세지 않으므로, 기동 중인 컨테이너가 unhealthy로 오판되지 않는다.

`eclipse-temurin:21-jre-alpine`에는 curl이 없다. BusyBox `wget`을 쓴다 — actuator health가 DOWN이면 503을 반환하고 `wget`이 non-zero로 끝나므로 상태 판정에 충분하다(JSON 파싱 불필요).

### 6. 스모크 스크립트에 `jq`를 쓰지 않는다

처음엔 `jq`로 파싱했다. `ubuntu-latest` 러너에는 기본 탑재라 CI에서는 동작한다. 그런데 **로컬 검증 중 이 머신에 `jq`가 없어 조용히 빈 문자열이 나오는 걸 발견했다** — `|| true`에 삼켜져서 "리비전 불일치"로 보였다.

CI에서는 문제가 없었겠지만, **배포 검증 스크립트가 개발자 머신에서 검증 불가능하면 그 자체가 결함이다.** 응답 형태가 `{"app":{"commit":"<sha>"}}`로 고정이라 `grep -o` + `cut`으로 충분하다. 이제 스크립트를 그대로 로컬에 붙여넣어 돌려볼 수 있다.

## 검증 방법

실제 postgres 컨테이너 + `db` 프로파일로 앱을 띄우고 CI 스모크 판정 로직을 그대로 재현했다.

```bash
docker run -d --name smoke-pg -e POSTGRES_PASSWORD=cookpilot -e POSTGRES_USER=cookpilot \
  -e POSTGRES_DB=cookpilot -p 55432:5432 postgres:16-alpine

APP_GIT_SHA=deadbeef1234 DB_URL=jdbc:postgresql://localhost:55432/cookpilot \
  DB_USERNAME=cookpilot DB_PASSWORD=cookpilot \
  ./gradlew bootRun --args='--spring.profiles.active=db'
```

결과:

- `/actuator/info` → `{"app":{"commit":"deadbeef1234"}}` — `GIT_SHA` 배선 확인
- `/actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}`
- 기대 SHA 일치 시 PASS, 다른 SHA 기대 시 불일치 판정 — **구 컨테이너를 새것으로 오인하지 않음 확인**
- `./gradlew test` 전체 통과, `docs/openapi.json` drift 없음

## 변경 목록

| 파일 | 변경 |
|---|---|
| `.github/workflows/ci.yml` | `build-args: GIT_SHA` 추가. `smoke` 잡 신설 (`needs: docker`, push 이벤트 한정) |
| `Dockerfile` | 런타임 스테이지에 `ARG GIT_SHA` → `ENV APP_GIT_SHA` |
| `src/main/resources/application.yml` | `management.endpoints.web.exposure.include: health,info`, `management.info.env.enabled`, `info.app.commit` |
| `docker-compose.prod.yml` | `app` 서비스에 `healthcheck` (wget, interval 15s, start_period 60s) |
| `README.md` | CI 섹션에 스모크 단계와 `DEPLOY_BASE_URL` 설명 추가 |
| `AGENTS.md` | 신설 — 기존 `CLAUDE.md` 내용을 정본으로 이관. 배포 스모크 불변조건 + "마이그레이션은 빈 DB에서만 검증된다" 경고 추가 |

스키마 변경 없음. API 변경 없음(actuator `/actuator/info` 노출만 추가, OpenAPI 스펙 불변).

## 알려진 약점 · 후속

- **롤백 경로는 여전히 없다.** `metadata-action`이 `type=sha` 태그를 만들지만 Watchtower는 `:latest`만 본다. 나쁜 이미지가 나가면 스모크가 CI를 빨갛게 만들어 **알려주긴 하지만**, 되돌리는 건 여전히 VPS SSH + 수동 태그 지정이다. 이 브랜치의 범위 밖.
- **데이터 의존 마이그레이션 실패는 여전히 사전에 못 잡는다.** 이번 변경은 그걸 *사후에 시끄럽게* 만들 뿐이다. 근본 해결은 운영 DB 스냅샷(또는 대표 시드)에 마이그레이션을 미리 돌려보는 것. `V8__add_cooking_result_identity.sql`이 backfill을 먼저 하는 좋은 선례지만, 이건 저자의 규율이지 게이트가 아니다.
- **`main` 워크플로가 최대 15분 길어진다.** PR은 영향 없다(`if: github.event_name == 'push'`). `concurrency.cancel-in-progress`가 켜져 있어 연속 머지 시 앞 런의 스모크가 취소될 수 있는데, 뒤 런이 어차피 최신 상태를 검증하므로 무해하다.
- **jar를 두 번 빌드하는 낭비는 그대로다.** `test` 잡이 컴파일한 결과를 버리고 `docker` 잡이 Dockerfile 안에서 Gradle을 처음부터 다시 돌린다. 정확성 문제가 아니라 성능 문제라 이번엔 손대지 않았다. 손댄다면 `Dockerfile`의 `RUN ./gradlew dependencies`(의존성 트리 출력용 태스크라 캐시를 의도만큼 못 채운다)를 BuildKit cache mount로 바꾸는 게 먼저다.
- **액션이 SHA로 핀되어 있지 않다.** `docker` 잡은 `packages: write`를 들고 있어 공급망 표면이긴 하다. 우선순위 낮음.

### 이 브랜치와 무관하게 발견된 기존 문제

검증 중 밟았다. **이 브랜치는 손대지 않았다.**

`./gradlew bootRun` (프로파일 없이)이 실패한다:

```
Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured.
Reason: Failed to determine a suitable driver class
```

`README.md`는 "기본 실행은 DB 없이 컨텍스트와 health를 확인하기 위한 용도"라고 적고 `curl localhost:8080/actuator/health`를 안내하지만, **실제로는 기동조차 되지 않는다.** 원인은 H2가 `testRuntimeOnly`라 런타임 클래스패스에 없는데 `spring-boot-starter-data-jpa`는 DataSource를 요구하기 때문. 테스트는 H2가 테스트 클래스패스에 있어서 통과한다.

선택지는 둘이고 성격이 다르다 — 결정 필요:
1. `testRuntimeOnly 'com.h2database:h2'` → `runtimeOnly` 로 바꿔 문서대로 동작하게 만든다 (운영 이미지에 H2가 딸려간다)
2. `README.md` / `AGENTS.md` 에서 "no-profile bootRun" 안내를 걷어내고 `db` 프로파일을 유일한 실행 경로로 명시한다
