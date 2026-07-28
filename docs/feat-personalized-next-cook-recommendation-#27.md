# #27 다음 조리 개인화 추천

## 현재 AI 구현 수준

이번 구현은 CookPilot 전용 모델을 새로 학습하거나 파인튜닝한 단계가 아니라,
**추천 규칙 엔진 → AI 설명 생성 → 추천 fallback**으로 이어지는 AI 호출
파이프라인을 먼저 구성한 것이다.

현재 베타에서는 Google AI Studio에서 발급한 API 키로 **Gemini Developer API
무료 티어**를 사용하는 것을 전제로 한다. 무료 티어 한도를 넘기거나 API를
사용할 수 없는 상황에서도 추천 수치 계산은 서버에서 계속 수행되고, 설명만
추천 fallback으로 전환된다. API 키는 앱에 넣지 않고 백엔드 환경변수로만
관리한다.

## 목표와 범위

사용자가 과거에 만족했던 실제 조리 변경을 다음 조리의 **선택 가능한 변경안**으로
제시한다. 추천은 원본 레시피나 개인 레시피 버전을 직접 수정하지 않고, 사용자가
수락한 경우에만 F-02 실행 스냅샷에 반영된다.

이번 범위는 재료의 **양 조절 추천**이다. 재료 대체, 조리 단계 변경, 벡터 검색,
자동 적용, 다른 요리 추천 피드 생성은 포함하지 않는다.

## 추천 계산 정책

추천 수치는 서버의 규칙 엔진이 결정한다. 역할을 셋으로 나눴다.

| 클래스 | 역할 |
| --- | --- |
| `RecommendationRuleEngine` | 판정 규칙(순수 함수, DB 무관). 임계값이 전부 여기 상수로 모여 있다 |
| `RecommendationDraftLoader` | 조회 + 트랜잭션. 규칙 엔진에 값 타입을 넘겨 추천 후보를 만든다 |
| `RecommendationService` | 후보에 설명 문구를 붙여 응답 조립 |

규칙 엔진은 `personalrecipe`의 `DiffComposer`와 같은 구조다. 정책을 바꿀 때는 규칙 엔진
파일만 보면 된다.

**설명 생성은 트랜잭션 밖에서 호출한다.** 조회는 `RecommendationDraftLoader`가 한
트랜잭션으로 끝내고, Gemini 호출은 그 뒤에 일어난다. 같은 트랜잭션 안에서 부르면 응답을
최대 6초(connect 2s + read 4s) 기다리는 동안 DB 커넥션을 붙잡아, 동시 요청 몇 건으로
커넥션 풀이 마른다.

### 패키지 구성

`recommendation`은 파일이 20개까지 늘어 관심사별 하위 패키지로 나눴다. 이 저장소에서
하위 패키지를 쓰는 첫 기능 패키지다.

| 패키지 | 내용 |
| --- | --- |
| `recommendation` | 컨트롤러·서비스·조회 로더·규칙 엔진·응답 DTO |
| `recommendation.explanation` | 설명 생성(클라이언트 인터페이스, Gemini 구현, 프롬프트, 설정) |
| `recommendation.feedback` | 피드백 요청·응답·엔티티·리포지토리 |
| `recommendation.profile` | 맛 프로파일 엔티티·리포지토리 |

`GeminiApi`(벤더 와이어 DTO), `RecommendationRuleEngine`, `RecommendationDraftLoader`는
package-private 이라 각각 `explanation`, `recommendation` 밖으로 새지 않는다.

1. 현재 사용자의 최근 만족도 4점 이상 조리 기록을 최대 100개 조회한다.
2. 각 기록이 새로 만든 개인 버전이 있으면 그 버전을 사용한다.
3. 새 버전 없이 기존 개인 버전을 다시 사용한 기록이면 선택했던 버전을 사용한다.
4. 원본 대비 재료 양만 바꾼 `MODIFY` 조정만 후보로 삼는다.
5. 대상 재료와 이름·단위가 같고 레시피 특성 유사도가 0.60 이상인 기록만 남긴다.
6. 서로 다른 만족 기록이 2회 이상일 때 가중 평균 비율을 계산한다.
7. 원본과 5% 미만 차이거나 0.25배 미만·2배 초과인 값은 추천하지 않는다.
8. 신뢰도 순으로 최대 3개를 반환한다.

같은 개인 버전을 두 번 만족스럽게 사용했다면 개인 버전 행은 하나여도 조리 기록은
두 개이므로 추천 근거 2회로 계산한다. 추천은 “버전 생성 횟수”가 아니라 “실제
만족한 조리 횟수”를 기준으로 한다.

## 레시피 특성

`recipe_flavor_profiles`는 레시피를 하나의 고정 클러스터로 분류하지 않고 다음
다중 특성을 저장한다.

- 음식 문화권 `cuisine`
- 음식 형태 `dish_type`
- 조리법 `cooking_methods`
- 양념 기반 `sauce_bases`
- 맛 특성 `flavor_tags`

현재 8개 카탈로그 레시피는 사람이 검수한 프로필을 Flyway V9에서 넣는다. 추천
유사도는 현재 문화권 0.25, 음식 형태 0.15, 조리법 0.15, 양념 기반 0.45로
계산한다. 같은 레시피는 유사도 1로 취급한다.

## Gemini와 추천 fallback

Gemini는 추천량을 계산하지 않는다. 서버가 계산한 추천량과 근거를 사용자가 읽기
쉬운 한 문장으로 설명하는 역할만 한다.

- 기본 모델: `gemini-3.5-flash`
- 기본 상태: 비활성화
- 활성화: `GEMINI_ENABLED=true`, `GEMINI_API_KEY` 설정
- 여러 추천 설명을 한 번의 구조화 JSON 요청으로 생성
- 연결 2초, 응답 4초 제한
- 비활성화, 시간 초과, 호출 실패, 응답 형식 오류 시 **추천 fallback** 사용

추천 fallback도 동일한 수치와 근거 횟수로 서버가 결정론적인 문장을 만든다.
따라서 Gemini가 없어도 추천 기능 자체는 정상 동작한다.

## API

### 다음 조리 추천

`GET /api/v1/recipes/{recipeId}/next-cook-recommendations`

응답에는 추천 ID, 대상 원본 재료, 원본·추천 양, 변경률, 신뢰도, 설명 출처,
근거가 된 조리 기록이 포함된다. 충분한 근거가 없으면 정상 응답의 추천 목록이
비어 있다.

### 추천 피드백

`PUT /api/v1/recipes/{recipeId}/recommendation-feedback/{recommendationId}`

사용자의 `ACCEPTED`, `REJECTED`, `MODIFIED` 선택을 저장한다. 같은 사용자와
추천 ID의 재전송은 같은 결과를 돌려준다. 거절한 재료 추천은 그 이후 새로운 만족
조리 근거가 생기기 전까지 다시 노출하지 않는다.

## DB 변경

`V9__add_next_cook_recommendations.sql`

- `recipe_flavor_profiles`: 레시피 다중 특성
- `recommendation_feedback`: 추천 수락·거절·수정과 설명 출처·근거 기록
- 현재 8개 레시피의 검수된 특성 seed

## 검증

- 비슷한 레시피의 반복 만족 변경으로 추천 생성
- 같은 개인 버전을 변경 없이 다시 조리한 기록도 별도 근거로 계산
- 근거 2회 미만이면 추천하지 않음
- Gemini 비활성화·실패·잘못된 응답에서 추천 fallback 사용
- 추천 피드백 멱등 저장
- 거절 뒤 새 근거가 생기기 전까지 동일 재료 추천 숨김
- 다른 사용자의 후기·버전·근거를 사용하지 않음
- Flyway V9와 PostgreSQL/JPA 통합 검증
- `RecommendationRuleEngineTest`: 유사도 가중치·컷라인 경계, 가중 평균, confidence
  상한, 근거 중복 제거, 거절 후 재제안 판정을 Docker 없이 검증(순수 단위 테스트)

실행 명령:

```bash
./gradlew test --no-daemon
./gradlew build --no-daemon
git diff --check
```
