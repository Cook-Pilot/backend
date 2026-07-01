# backend
Cook-Pilot의 Backend 레포입니다.

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
