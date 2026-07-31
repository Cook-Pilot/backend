# feat/recipe-edit-pipeline

## 무엇을 왜

조리 1회의 결과를 저장하는 경로가 둘로 갈라져 있었다. `POST /reviews` 가 리뷰를 저장하면서
실행 스냅샷(재료·단계 전체)을 같이 받아 **서버가 원본과 대조해 diff 를 역산**했고(`createFromExecution`,
~250줄), 별도로 선언적 diff 를 받는 `derive` 가 서비스 레벨에만 있었다. CLAUDE.md 는
"diff 계약은 명시적"이라 적어놨는데 실제 주력 경로는 역산이었다.

프론트는 이미 ADD/MODIFY/REMOVE 를 알고 있다(`_changeLabels` 가 그걸로 라벨을 만든다).
정보를 flat 스냅샷으로 뭉개 보냈다가 서버가 다시 추측하는 구조라, 그 250줄을 지우고
**클라가 타입 붙은 diff 를 그대로 보내는 하나의 경로**로 합쳤다. 부수 효과로 암시적 수정
(조리 중·후 자연어 → LLM)의 출력도 같은 diff 타입으로 강제할 자리가 생긴다.

- 버전 생성이 `POST /reviews` 에서 분리돼 `POST /reviews/{reviewId}/personal-versions` 가 됐다
- `RecipeEditRequest` 는 층 모델(setup → cooking → review)의 입력. 지금 살아있는 층은 setup 하나
- 삭제: `createFromExecution`, `derive`, `ExecutedRecipe`, `DeriveVersionRequest`, `PersonalRecipeDeriveTest`

## 핵심 설계 결정

### 1. 요청 순서 — 리뷰가 먼저 (review-first)

`POST /reviews` → `POST /reviews/{reviewId}/personal-versions`. 경로가 리뷰를 지목한다.

리뷰는 `clientSessionId` 로 멱등해서 재시도해도 `reviewId` 가 안 바뀐다. 그 안정된 id 를
경로로 받으면 세션 역추적이 필요 없고, `source_review_id` 가 null 로 빠지는 경로가 사라진다 —
#28 추천의 "이 조리가 만든 버전" 증거, 인분 프리필 조인, 멱등 게이트가 한꺼번에 닫힌다.

**대가:** 리뷰 없이 버전만 만드는 경로가 없다. 조리 1회 = 리뷰 1 + 버전 0~1 이라 지금은
맞지만, "레시피만 편집하고 저장" 기능이 생기면 별도 라우트가 필요하다.

### 2. 조리 1회의 사실은 리뷰 행에서 읽는다 (요청 본문에 없다)

`RecipeEditRequest` 는 **어디에도 저장된 적 없는 것만** 담는다 → `{setup, cooking}` 둘뿐.

| 값 | 출처 | 요청 본문에 두면 |
|---|---|---|
| `recipeId` | `post_cook_reviews.recipe_id` | 리뷰와 다른 레시피를 지목해도 안 걸림 |
| `targetServings` | `target_servings` | 리뷰는 4인분, 본문은 3인분 → 양이 조용히 어긋난 채 base 기준으로 저장 |
| 계보 부모 | `source_personal_version_id` | 실제 조리한 버전과 다른 부모 |
| review 층 입력 | `comment` / `next_time_note` | 리뷰에 적힌 것과 다른 문장을 LLM 에 먹임 |

같은 사실의 **쓰기 가능한 사본이 둘**이 되면 어긋나도 에러가 나지 않는다. diff 는 base 기준
절대량으로 저장되므로 되돌릴 수도 없다. 반대 방향(요청을 정본으로)은 성립하지 않는다 —
`target_servings` 컬럼은 인분 프리필과 #28 이 읽으므로 지울 수 없고, 남겨두면 리뷰 행이
조용히 거짓이 되는 경로가 열린다.

비용은 `findByIdAndUserId(reviewId, userId)` PK 조회 1회. 그 대가로 없어진 것:
요청 필드 4개, 대조 규칙(같은 수만큼 필요했을), FK 이름 화이트리스트 분기,
그리고 `TODO(인증 붙은 뒤) 리뷰 소유자·레시피 대조` 두 줄 — 조회가 소유자 스코프라
남의 리뷰는 404 이고, 레시피를 리뷰에서 뽑으니 불일치 케이스는 **표현 자체가 불가능**해졌다.

### 3. 응답은 아무도 안 읽는다 — 상태코드로 사정을 표현하지 않는다

클라 흐름은 `POST /reviews` 로 id 를 받고 → 그 id 로 버전 생성을 부르는 게 끝이다. 두 번째
응답의 본문·상태코드를 읽고 분기하는 화면이 없다. 그래서 멱등 히트를 200 으로 가르는
설계를 넣었다가 **뺐다** — 읽는 쪽이 없는 구분은 서비스 반환 타입까지 오염시키는 순수 비용이다.

반대로 남긴 구분은 하나뿐: **204**(수정 결과가 원본과 같아 버전이 없음). 이건 도달하는
경로가 실제로 있고(인분만 조절한 조리) 클라가 "이 조리가 만든 버전"으로 이동할 수 없다는
뜻이라 의미가 있다. 필요하면 나중에 `GET /reviews/{id}.createdPersonalVersionId` 로도 알 수 있다.

같은 잣대로, 검증은 **응답을 예쁘게 만들려고**가 아니라 **틀린 데이터가 저장되는 걸 막으려고**만
남긴다(아래).

### 4. 검증 위치는 "그 값이 저장되는 곳"

리뷰가 먼저 저장되므로, 리뷰 행의 값이 나중에 400 을 내면 **쓸 수 없는 조리 기록만 남는다**.
그래서 두 검증이 `ReviewService.submit` 으로 올라갔다:

- `targetServings` 가 있는데 0 이하 → 400
- `sourcePersonalVersionId` 가 이 사용자·이 레시피의 것인지 (`PersonalRecipeService.validateSourceVersion`,
  버전 생성과 공유) — 계보 부모의 근거라 무검증이면 남의 버전이 부모로 박힌다

`applySetup` 의 "재료를 고치는데 인분을 모르면 400" 은 그대로 방어선으로 남는다
(리뷰의 `target_servings` 는 nullable — 단계만 고치는 수정은 인분이 필요 없다).

## 스키마 / API 변경

**스키마 변경 없음.** 마이그레이션 추가 0. 쓰는 컬럼·FK·인덱스는 전부 기존 것
(`personal_recipe_versions.source_review_id`, 부분 유니크 인덱스 `uq_personal_versions_source_review`
= `V8__add_cooking_result_identity.sql:24-26`).

**API 변경 (미출시라 깨질 클라 없음, 프론트 계약은 바뀜):**

| 라우트 | 변화 |
|---|---|
| `POST /api/v1/reviews` | 요청에서 `ingredients` / `steps`(실행 스냅샷) **제거**. 리뷰는 "무엇을 조리했고 어땠는지"만 기록한다. 응답의 `createdPersonalVersionId` 는 저장 시점엔 항상 null 이고 조회 시 역참조로 채워진다 |
| `POST /api/v1/reviews/{reviewId}/personal-versions` | **신설.** 본문 `{setup: {ingredientAdjustments, stepAdjustments}, cooking: {transcript}}`. 201 생성(재전송으로 기존 버전을 돌려줄 때도 201 — 히트를 200 으로 가르지 않는다, 아래) / 204 수정 결과가 원본과 같음 / 404 없는 리뷰·레시피 / 400 검증 실패 |
| `GET /api/v1/reviews/{id}`, `GET /api/v1/recipes/{id}/reviews` | 응답에 `createdPersonalVersionId` 가 채워진다(이전엔 항상 null) |

이 브랜치 중간에 있던 `POST /recipes/{recipeId}/personal-versions` 는 삭제됐다 —
경로에서 `recipeId` 를 빼면 레시피 불일치 케이스가 구조적으로 사라진다.

## 리뷰에서 잡은 것 (수정 완료)

DB 까지 도달해서 500 이 되던 입력 2종과 조용히 틀리던 입력 1종.

**1. 없는 reviewId → 500 이었다.** 서버가 리뷰를 읽지 않고 `source_review_id` FK 에 존재
확인을 맡긴 설계라, FK 가 잡으면 `DataIntegrityViolationException` → 500. 한때
`GlobalExceptionHandler` 에 제약 이름 화이트리스트로 404 를 만들었지만, 위 결정 2 로
리뷰를 직접 읽게 되면서 **그 분기는 죽어서 제거**했다(404 는 이제 조회에서 나온다).
CHECK 제약 두 개에 대한 화이트리스트는 안전망으로 남는다 — 통째로 4xx 로 내리면
NOT NULL 누락·UNIQUE 충돌 같은 진짜 서버 버그가 클라 잘못으로 위장돼 로그에서 사라진다.

**2. MODIFY/REMOVE 단계에 `insertAfterStepIndex` 를 주면 500 이었다.** 앵커는 ADD 전용이고
V2 CHECK 가 비ADD 에는 NULL 을 요구하는데 `validateStepAdjustments` 는 ADD 쪽만 봤다.
`validate()` 가 선언한 계약("DB 가 잡으면 500 이니 여기서 400")의 유일한 구멍이었다. 서비스에서 400.

**3. 인분을 모른 채 재료를 고치면 조리 인분 양이 그대로 저장됐다.** `normalizeAmounts` 가
null 이면 조용히 원본을 반환해서, 4인분 2000ml 가 1인분 레시피의 2000ml 가 됐다 — 에러 없이 4배 오염.
재료 조정이 하나라도 있으면 인분 필수(400). 단계만 고치는 수정은 양과 무관하므로 요구하지 않는다.

## 2차 리뷰(Codex)에서 잡은 것 (수정 완료)

**4. 멱등 게이트가 소유자 확인보다 앞에 있었다.** `findFirstBySourceReviewId` 는 userId 스코프가
아니다. 남의 reviewId 를 지목하면 소유자 조회(`findByIdAndUserId`)의 404 가 나기 전에 그 사람의
버전 DTO(userId·recipeId·계보 포함)가 그대로 나갔다. 두 조회의 순서를 바꿔 소유자 확인을 먼저 한다 —
사용자 행 락은 여전히 맨 앞이라 동시 재전송 직렬화는 그대로다.

**5. 값 불변식 검증이 스냅샷 경로와 함께 삭제됐다.** 지운 `createFromExecution` 안에 있던
`amount >= 0`, `name/instruction 비공백` 검사가 새 경로에는 없었다. 공백 문자열은 non-null 이라
합성기가 원본을 덮어써서 이름 없는 재료가 렌더되고, 음수 amount 는 컬럼에 CHECK 가 없어 그대로 저장된다.
`validate()` 로 옮겼다(음수 amount / 음수 timerSeconds / 공백 name·instruction → 400).

**6. 한 원본 행에 조정을 둘 이상 보낼 수 있었다.** `DiffComposer` 는 원본 id 를 키로 맵에 담으므로
둘 중 하나가 조용히 사라지고, sort_order 가 같으면 어느 쪽이 남는지도 정해지지 않는다.
`(version, original_id)` UNIQUE 는 없고, 있더라도 "어느 쪽이 이기는가"는 DB 가 답할 수 없는 질문이다.
검증에서 중복 참조를 거절한다(400).

**7. 조정 목록 안의 JSON `null` 이 500 이었다.** `[null]` 이 그대로 역직렬화돼 `adj.type()` 에서 NPE.
빈 항목을 명시적으로 거절하고, 동시에 `applySetup` 에서 **검증을 정규화보다 앞으로** 옮겼다 —
`normalizeAmounts` 가 요청 본문을 먼저 훑기 때문에 순서가 반대면 검증 전에 NPE 로 터진다.
되돌리기는 부호도 null 여부도 바꾸지 않아 검증 결과는 순서와 무관하다.

### 함께 바뀐 것

- **`RecipeEditPipelineApiTest` 에서 `@Transactional` 제거.** 붙어 있으면 서비스 트랜잭션이
  테스트 트랜잭션에 합류해 커밋이 없고, flush 가 후속 조회에 딸려갈 때만 일어난다 —
  위 1·2 를 `@Transactional` 인 채로 재현하니 둘 다 201 이 나왔다. 제약을 타야 회귀 테스트가
  성립한다. 대신 버전 번호가 클래스 간 누적되므로 절대값 대신 계보와 상대값만 단언한다.
- 테스트가 조건을 **요청이 아니라 리뷰로** 만든다. 인분·부모 버전·리뷰 본문이 전부 리뷰 행에서
  오므로 `submitReview(recipeId, targetServings, sourceVersionId, comment)` 가 픽스처의 중심이 됐다.
- `personalrecipe → review` 의존이 새로 생겼다. 패키지 참조는 양방향이지만 빈 순환은 아니다 —
  주입 대상이 `ReviewService` 가 아니라 `PostCookReviewRepository`(Spring Data 프록시)다.

### 남긴 것 (이번 범위 밖)

- 생성 응답의 `createdAt` 이 항상 null — `@CreationTimestamp` 는 flush 때 채워지는데 DTO 를 그 전에 만든다.
- `RecipeEditRequest.hasSetupEdits()` / `hasCookingTranscript()` 호출부 0건.
- `ReviewService.findVersionIdsByReviewIds` 가 userId 스코프가 아님(`findByUserIdAndSourceReviewIdIn` 이 이미 있음).
- CLAUDE.md 가 `is_default` 승격/강등을 기술하는데 프로덕션 코드에 `setDefault()` 호출 0건(이 브랜치 이전부터).
- 인분 프리필(B안: `source_review_id` → `target_servings` 조인)은 **노출이 아직 안 됐다** —
  조리 이력 응답과 프론트 `CookingHistoryEntry` 에 `targetServings` 필드가 없다. 스키마 변경은 0.

## 후속 — cooking / review 층 LLM 계약 (설계 확정, 미구현)

두 층은 지금 앞 층 결과를 그대로 통과시킨다(`TODO(AI 확정 후)`). 배관을 넣을 때의 계약:

- **어휘는 `ADD`/`MODIFY`/`REMOVE` 3개뿐.** `CANCEL` 같은 op 를 만들지 않는다 — diff 는 항상
  원본 기준 누적이라 각 층 출력이 그 자체로 완결이고 **부재 = 취소**다. 두 번째 어휘를 만들면
  LLM 출력 타입 ≠ 저장 타입이 되고 매핑 코드와 매핑 버그가 생긴다.
- **출력은 `IngredientAdjustment` 와 필드 1:1**, 차이 3개: `originalIngredientId`(UUID) →
  `target`(원본 행 번호 정수, 서버가 UUID 로 매핑 — UUID 를 뱉게 하면 환각 + 토큰 낭비),
  `sortOrder` 없음(배열 순서로 서버가 부여), `reason` 추가(사후 검증 + 사용자 설명).
- **판별 유니온(`oneOf`) 쓰지 않는다.** 타입별 필수 필드가 다른 걸 스키마로 표현하면 모델이 자주
  틀린다. flat record + nullable 로 두고 타입별 필수 검사는 서버가 한다 — 기존
  `IngredientAdjustment` + `validateIngredientAdjustments` 가 이미 그 모양이다.
- **프롬프트의 "현재 상태"는 diff 가 아니라 `DiffComposer` 합성 결과.** 모델은
  `MODIFY(amount=1.5)` 를 못 읽고 "고추장 1.5스푼"은 읽는다.
- **LLM 은 신뢰 경계 밖.** `responseJsonSchema` 는 모양만 보장한다. `target` 범위, 타입별 필수 필드,
  음수 금지를 서버가 다시 본다. 추가로 **유실 검사** — 이전 층 diff 항목이 결과에서 사라졌으면
  그에 대한 `reason` 이 있어야 통과.
- **배관은 이미 있다** (`GeminiApi.GenerationConfig` 의 `responseMimeType` + `responseJsonSchema`).
  재검토 2개: `thinkingBudget` 이 현재 0(설명문 생성용으로 끔 — 인과추론엔 예산이 필요할 수 있음),
  `read-timeout: 4s`(조리 중 조언엔 충분하나 긴 리뷰 추론엔 부족할 수 있어 층별 분리 검토).

**인과추론(양파 → 단맛)은 이 파이프라인에 섞지 않는다.** "발화 → diff 변환"은 구조적이라 검증
가능하고, "증상 → 원인 → 처방"은 인과추론이라 불가능하다. 게다가 "양파 빼기 vs 간장 추가"는
취향이라 정답이 없다 — 모델 등급을 올려도 원인 *추정* 정확도만 오른다. 따라서 **추론은 LLM,
결정은 사용자**: 선택지를 제시하고 사용자가 고른 순간 그것이 정형 데이터다. UI 에서 행을 직접
지목하므로 "계란/달걀" name-matching 문제도 원천 소멸한다.

조리 후가 조리 중보다 위험하다 — 조리 중 조언은 맛보고 되돌릴 수 있지만(피드백 루프), 조리 후
해석은 틀려도 다음 조리까지 모른 채 개인 레시피가 오염된다. **review 층은 사용자 확인 UI 필수.**

## 후속 — 부모 버전 대비 변경점 (설계 확정, 미구현)

**문제.** diff 는 항상 원본 기준 누적이므로 프론트는 "원본 → vN" 은 완전히 알 수 있다
(`PersonalRecipeVersionDetail` 이 원시 diff + 항목별 `origin` 을 이미 준다).
반대로 "v1 → v2" 는 알 수 없다. 두 버전을 각각 받아 비교해도 `ADD` 행은 원본 참조도
자기 식별자도 없어서 버전 간 짝짓기가 불가능하다(중복 이름 ADD 허용이므로 이름 매칭도 못 씀).

**결정.**

| 화면 | 무엇 | 상태 |
|---|---|---|
| 메인 | 원본 대비 변경점 | `GET /personal-versions/{id}` — 이미 완성, 손 안 댐 |
| 요청 시 | **부모 1단계** 대비 변경점 | 신설. 계보 전체를 거슬러 올라가지 않는다 |

별도 엔드포인트로 뺀다 — 상세 조회(핫패스)에 쿼리를 늘리지 않기 위해.

```
GET /api/v1/personal-versions/{versionId}/parent-delta
  → 200 ParentDelta
  → 204 부모 없음(v1)
```

```java
/** 부모 버전 1단계 대비 변경점. 계보 전체를 거슬러 올라가지 않는다. */
public record ParentDelta(
        UUID parentVersionId,
        int parentVersionNumber,
        List<IngredientChange> ingredientChanges,
        List<StepChange> stepChanges
) {
    /** before == null → 이 버전에서 되살아남. after == null → 이 버전에서 빠짐. */
    public record IngredientChange(ComposedIngredient before, ComposedIngredient after) {}
    public record StepChange(ComposedStep before, ComposedStep after) {}
}
```

### 근거

- **adjustment 셋이 아니라 합성 결과를 비교한다.** 두 diff 를 직접 비교하면
  "`MODIFY.amount` 가 null → 1.5" 같은 diff 의 diff 가 나온다. 사용자에게 무의미하고
  비교 규칙을 새로 만들어야 한다. 부모와 자기를 각각 `DiffComposer` 로 합성해서 결과
  리스트를 비교하면 새 규칙이 0개고 `before`/`after` 가 화면에 그대로 쓰는 실제 값이 된다.
- **변경된 항목만 담는다.** `before`/`after` 중 하나가 null 로 생김/사라짐을 표현 —
  별도 `kind` enum 불필요.
- **서버는 문자열 포맷을 만들지 않는다.** `"1.5스푼 → 2스푼"` 조립은 프론트 몫이고
  `_changeLabels` 에 이미 있다. `summary` 컬럼(사람 읽는 한 줄 라벨)과 역할이 다르다.
- 키는 `originalIngredientId` / `originalStepId`. 부모 조회는 기존 `findEntity`
  (userId 스코프) 재사용 → 남의 버전을 `parentVersionId` 로 지목한 데이터에 대한 방어가 공짜.

### 알려진 한계

**`ADD` 는 델타에서 빠진다.** 버전 간 안정 식별자가 없어서. 지금은 재료·단계 추가 UI 가
없어 도달 불가 경로이므로 실질 손실이 없다. ADD UI 를 붙일 때 함께 해결한다:

- `personal_ingredient_adjustments` / `personal_step_adjustments` 에 `add_lineage_id UUID NULL` 추가.
  새 ADD 는 자기 id, 부모에서 이어받은 ADD 는 부모 행의 값 복사.
- `RecipeEditRequest` 는 클라가 **원본 기준 누적 전체 집합**을 보내는 구조다 — 서버가 부모 diff 를
  복사하는 게 아니라 클라가 이미 머지해서 보낸다. 따라서 lineage 는 서버 혼자 만들 수 없고
  **클라가 유지한 ADD 의 lineage id 를 돌려보내야** 한다. LLM 층도 같다
  (`target` 정수 매핑을 ADD 에도 확장 — 이전 층 diff 렌더에 안정 라벨을 붙이고 서버가 UUID 로 매핑).
- 즉 클라 계약 + LLM 스키마 동시 변경이라, ADD UI 가 없는 지금 하면 검증 못 하는 코드만 늘어난다.

### 착수 순서

선행 조건이던 `createFromEdits` 는 이 브랜치에서 완성됐다(버전을 만들 경로가 없으면
parent-delta 의 통합 테스트를 붙일 수 없었다). 이제 막는 것은 없다 — 비교 자체는 순수 함수다.
