# F-10 조리 기록 및 개인 레시피 버전

## 확정한 정책

- 조리를 마치면 후기와 실제 조리 시각을 항상 `post_cook_reviews`에 저장한다.
- 원본 레시피(`recipes`, `recipe_ingredients`, `recipe_steps`)는 수정하지 않는다.
- 실행한 재료·단계가 선택한 버전과 다를 때만 새 개인 버전 `vN`을 만든다.
- 별점·메모만 달라진 경우와 인분수에 따른 단순 비례 조정은 새 버전을 만들지 않는다.
- 최신 버전을 자동 기본값으로 지정하지 않는다. 사용자가 원본 또는 최근 개인 버전 중 하나를 직접 고른다.
- 같은 레시피의 개인 버전 목록은 최근 5개까지만 제공한다.

## 저장 흐름

1. 프론트는 조리 시작 시 만든 `clientSessionId`를 완료 요청에도 그대로 보낸다.
2. 서버는 `(user_id, client_session_id)`로 이미 저장된 조리인지 확인한다.
3. 처음 받은 요청이면 후기를 먼저 저장한다.
4. 실제 실행 스냅샷을 원본 레시피 기준으로 정규화해 누적 diff를 계산한다.
5. 선택한 개인 버전의 누적 diff와 새 diff가 다를 때만 `personal_recipe_versions`와 조정 행을 저장한다.
6. 재전송이면 기존 후기와 기존 생성 버전 ID를 그대로 반환한다.

`targetServings`가 2이고 원본이 1인분이면 실제 재료량을 다시 1인분 기준으로 환산한 뒤 비교한다. 따라서 밥 1공기가 2공기로 늘어난 것만으로는 개인 취향 변경이 되지 않는다.

## 후기 저장 API

`POST /api/v1/reviews`

핵심 요청 필드:

- `clientSessionId`: 중복 저장 방지용 조리 UUID
- `recipeId`: 원본 레시피
- `cookedAt`: 실제 조리 완료 시각
- `targetServings`: 이번 조리 인분
- `sourcePersonalVersionId`: 이번에 선택해 사용한 개인 버전, 원본이면 `null`
- `rating`, `comment`, `nextTimeNote`: 사용자 후기
- `ingredients`, `steps`: 실제 실행한 최종 스냅샷

응답의 `createdPersonalVersionId`는 실제 변경으로 새 버전을 만들었을 때만 값이 있고, 후기만 저장한 경우 `null`이다.

## 실행 가능한 변경 판정

재료:

- 원본 재료의 양·이름·단위·필수 여부 변경
- 원본 재료 생략
- 새 재료 추가

조리 단계:

- 설명·타이머·주의 문구 변경
- 원본 단계 생략
- 새 단계 추가

MVP에서는 기존 타이머나 주의 문구를 완전히 삭제하는 편집은 제공하지 않는다.

## 조리 이력 API

`GET /api/v1/cooking-history?from=<ISO-8601 Instant>&to=<ISO-8601 Instant>`

현재 사용자에 대해 `from` 이상, `to` 미만의 조리 이력을 실제 조리 시각 역순으로 반환한다. 달력 UI는 별도 달력 테이블 없이 이 API 결과를 날짜별로 묶어 사용한다.

응답에는 레시피 정보, 후기, 사용한 개인 버전 ID, 이번 조리로 새로 생성된 개인 버전의 번호와 요약이 포함된다.

## DB 변경

`V8__add_cooking_result_identity.sql`

- `post_cook_reviews.client_session_id`
- `post_cook_reviews.cooked_at`
- `post_cook_reviews.source_personal_version_id`
- `post_cook_reviews.target_servings`
- 사용자·조리 세션 중복 방지 유니크 인덱스
- 기간별 이력 조회 인덱스
- 리뷰 하나가 개인 버전을 둘 이상 만들지 못하게 하는 유니크 인덱스

## 검증

- 실행 변경이 없을 때 후기만 저장
- 인분 비례 계산을 변경으로 오판하지 않음
- 실제 변경이 있을 때만 누적 diff 버전 생성
- 선택한 개인 버전을 그대로 다시 조리해도 중복 버전 미생성
- 동일 조리 세션 재전송 시 같은 결과 반환
- 최근 개인 버전 5개 제한
- 기간별 조리 이력 조회
- 기존 사용자 분리, 최근 조리, JPA/Flyway 테스트 회귀 확인

실행 명령:

```bash
./gradlew test --no-daemon
./gradlew build --no-daemon
git diff --check
```
