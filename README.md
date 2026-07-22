# backend

[![CI](https://github.com/Cook-Pilot/backend/actions/workflows/ci.yml/badge.svg)](https://github.com/Cook-Pilot/backend/actions/workflows/ci.yml)

Cook-Pilot의 Backend 레포입니다.

---

# Tech Stack

| 항목 | 버전 |
| --- | --- |
| Spring Boot | 4.1.0 |
| Java | 21 |
| Build Tool | Gradle |
| DB | PostgreSQL (연결 준비만, repository 계층은 AI 파트 확정 후 도입) |

---

# 실행 방법

## 요구 사항

- JDK 21 (`java -version` 으로 확인)

## 실행

```bash
# 애플리케이션 실행
./gradlew bootRun

# 빌드
./gradlew build

# 테스트
./gradlew test
```

- 기본 포트: `8080`
- Health 체크: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- 기본 실행은 DB 없이 동작합니다. PostgreSQL 연결 시:

```bash
./gradlew bootRun --args='--spring.profiles.active=db'
```

## 컨테이너 실행

```bash
docker compose up --build      # 앱 + PostgreSQL 기동
```

DB 접속 정보는 전부 환경변수로 주입되므로 이미지 재빌드 없이 교체할 수 있습니다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `db` | `db` 프로파일 활성화. 비우면 DB 없이 동작 |
| `DB_URL` | `jdbc:postgresql://db:5432/cookpilot` | JDBC URL |
| `DB_USERNAME` | `cookpilot` | 계정 |
| `DB_PASSWORD` | `cookpilot` | 비밀번호 |

외부 DB로 전환:

```bash
DB_URL=jdbc:postgresql://<host>:5432/cookpilot \
DB_USERNAME=<user> DB_PASSWORD=<pass> docker compose up app
```

> 배포 환경의 기본 비밀번호는 반드시 교체하십시오. 위 기본값은 로컬 개발 전용입니다.

## 배포 (VPS + Watchtower)

`main` 푸시 → CI가 ARM64 이미지를 빌드해 `ghcr.io/cook-pilot/backend:latest` 갱신 → VPS의 Watchtower가 폴링으로 감지해 앱 컨테이너를 자동 교체합니다. VPS에서 별도 조작이 필요 없습니다.

### 최초 1회 세팅

**1. GHCR 패키지를 public으로 전환**
레포 → Packages → `backend` → Package settings → Change visibility → Public.
(private로 두려면 VPS에서 `read:packages` 스코프 PAT로 `docker login ghcr.io` 후, `docker-compose.prod.yml`의 `config.json` 마운트 주석을 해제하십시오.)

**2. VPS에서 기동**

```bash
git clone https://github.com/Cook-Pilot/backend.git && cd backend
cp .env.example .env
vi .env                    # POSTGRES_PASSWORD 반드시 교체
docker compose -f docker-compose.prod.yml up -d
```

### 확인

```bash
docker compose -f docker-compose.prod.yml ps
curl http://localhost:8080/actuator/health          # {"status":"UP"}
docker compose -f docker-compose.prod.yml logs -f watchtower
```

### 구성 메모

- Watchtower는 `--label-enable`로 **`app` 컨테이너만** 감시합니다. postgres는 라벨이 없어 자동 업데이트되지 않습니다 (DB 메이저 버전이 멋대로 올라가는 사고 방지).
- 컨테이너 교체 시 기존 env·볼륨·포트 설정은 그대로 유지됩니다. `.env`의 시크릿은 배포마다 살아남습니다.
- postgres는 호스트에 포트를 열지 않습니다. 앱 컨테이너만 내부 네트워크로 접근합니다.
- 폴링 주기는 `.env`의 `WATCHTOWER_INTERVAL`(기본 300초). main 푸시 후 최대 그 시간만큼 지연됩니다.
- Watchtower는 `/var/run/docker.sock`을 마운트합니다. 이 컨테이너가 침해되면 호스트 전체 권한과 동등합니다 — 신뢰하는 이미지만 감시 대상에 두십시오.

---

# 프로젝트 구조

패키지는 계층이 아니라 **기능별 집합**으로 나눕니다.

```
com.cookpilot.backend
├── recipe/          레시피 조회 (F-01)
├── review/          조리 후 피드백 = 조리 1회의 기록 (F-10)
├── personalrecipe/  개인 레시피 버전 (F-11~12)
├── ai/              LLM 예외 피드백 — AI 파트 미확정, mock 응답 (F-08)
├── user/            고정 목유저 (인증 미도입)
└── common/          공통 예외 처리 (ProblemDetail)
```

- API 명세와 확정 결정 사항: [`docs/08_mvp_api.md`](docs/08_mvp_api.md)
- 기획/설계 문서: [`docs/`](docs/README.md)

---

# CI

PR과 `main` push마다 GitHub Actions가 `./gradlew test`를 실행합니다.
워크플로: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

---

# Git Convention

## TAG

| 태그 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 / 코드 추가 |
| `fix` | 버그 · 문제점 수정 |
| `refactor` | 동작 변화 없는 코드 리팩토링 |
| `comment` | 주석 추가 · 수정 (코드 변경 X), 오타 수정 |
| `docs` | README 등 문서 수정 |
| `rename` | 파일 · 폴더명 수정 또는 이동 |
| `chore` | 패키지 추가, 설정 변경 등 그 외 잡일 |

## Branch Name

```
(TAG)/(주요내용)
```
**예시**

```
feat/login-page
fix/token-expire-#99
chore/eslint-config
```
## Commit Message

```
(TAG)((ISSUE)) : 제목
```
- 이슈 번호는 있을 때만, 없으면 생략.

**예시**

```
feat(#123) : 로그인 API 연동을 구현하였다.
- auth.ts 추가
- 토큰 갱신 로직 처리
```

```
chore : eslint, prettier 패키지 추가
```

## Pull Request

- 제목: `(TAG) : 요약`
  - 예) `feat : 로그인 페이지 구현`
  - PR 번호는 GitHub이 자동으로 붙이므로 직접 적지 않습니다.
- `main`은 직접 push 금지. **PR로만 머지**합니다.
