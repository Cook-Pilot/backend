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
./gradlew bootRun --args='--spring.profiles.active=local-db'
```

---

# 프로젝트 구조

패키지는 계층이 아니라 **기능별 집합**으로 나눕니다.

```
com.cookpilot.backend
├── recipe/          레시피 조회 (F-01)
├── cooksession/     조리 세션 · 단계 이동 · 이벤트 기록 (F-02~04, F-09)
├── review/          조리 후 피드백 (F-10)
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
