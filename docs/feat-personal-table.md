# feat/personal-table — 개인 레시피 diff 영속화

> 이 문서는 `feat/personal-table` 브랜치를 코드 안 읽고 이해하기 위한 자료다.
> 상세 구현 리포트: `docs/feat-personal-table-report.html` · 현재 스키마: `docs/schema-current.md`

---

## 1. 무엇을 왜 바꿨나

개인 레시피 버전의 조정값이 opaque JSONB(`adjustment_payload`) 하나에 뭉쳐 있었다. 문제:

- **추적 불가**: 깊이·복잡도가 쌓일수록 "뭘 바꿨는지" JSON 안에서 추적이 어려웠다.
- **단계 추가 불가**: JSON 구조로는 "원본 2번 단계 뒤에 소금 추가" 같은 단계 삽입을 사실상 표현 못 했다.

이번 변경으로:
1. 조정값을 **관계형 diff 테이블 2개**(재료/단계)로 옮겼다.
2. 인메모리(`ConcurrentHashMap`)로만 있던 개인 버전·리뷰 저장을 실제 **PostgreSQL(JPA)** 로 전환했다.

---

## 2. 핵심 설계 결정과 근거 (2026-07-22 확정)

| 항목 | 결정 | 근거 |
|------|------|------|
| 표현 방식 | **diff 행** — 변경분만 저장, 렌더는 원본+diff 합성. 스냅샷 복사 아님 | 저장 효율 + 변경점 추적성 |
| diff 기준 | **항상 원본 기준 누적** — 어떤 버전이든 `원본 + 그 버전 diff` 만으로 렌더 끝. 부모체인 재생 없음 | 렌더 O(1) 버전깊이. 깊은 계보서도 지연 안 늘어남 |
| 두 번 바뀜 (v2→v3) | v3 는 **부모 diff 를 통째 복사 후 수정**한 전체 집합을 저장 | "원본 기준 누적" 불변식 자동 유지. 체인 재생 회피 |
| 중복 문제 | v3 가 v2 diff 복붙 → 중복 발생하나 **의도된 가격** | 자기완결성(원본+diff 단독 렌더)의 대가. row 는 작아 무시 가능 |
| 새 재료 | `ADD` 는 원본 참조 없이 자기 데이터 통째 보유 | 원본에 없는 재료/단계 표현. DB CHECK 로 강제 |
| 단계 변경 | 재료와 동일한 ADD/MODIFY/REMOVE. 끼워넣기는 `insert_after_step_index` 앵커(-1=맨앞) | 단계 삽입 표현 |
| payload 운명 | `adjustment_payload` 컬럼 삭제 | 입력은 정제된 diff 로만. 현재 내용 전부 목데이터라 손실 없음 |
| 리뷰 영속화 | 리뷰 먼저 저장 → 버전이 `source_review_id` 로 역참조 | 추적 안 끊김 |
| API 경로 | 신규 경로 설계 보류. 기존 GET 2개만 응답 형태 갱신, 파생은 서비스 레이어까지 | (사용자 확인: 특별 이유 없음 — 후속서 엔드포인트 추가 가능) |

---

## 3. 합성(렌더링) 규칙 — `DiffComposer` (순수 함수)

원본 + diff → 완성된 내 레시피. DB 무관 순수 함수라 Testcontainers 없이 테스트된다.

- **MODIFY**: NULL 필드 = 원본 유지, non-NULL = 오버라이드 (수량만 바꾸기 / 행위 통째 교체 둘 다 커버)
- **ADD(재료)**: 결과 뒤에 붙임, `sort_order` 순
- **ADD(단계)**: 앵커 뒤 삽입, 같은 앵커끼리 `sort_order`, `stepIndex` 0부터 재부여
- **REMOVE**: 결과에서 제외. 제거된 단계 뒤 앵커된 ADD 는 살아남음(앵커는 원본 인덱스 기준)
- 범위 밖 앵커(원본 3개인데 anchor=99)는 맨 뒤에 붙음. -1 은 맨 앞

### 파생 시맨틱
```
원본 ──diff──▶ v1 ──(부모 diff 복사 후 수정)──▶ v2 ──▶ v3 …
렌더링은 언제나: 원본 + 해당 버전 diff (체인 재생 없음)
```
파생 요청의 diff 가 `null` 이면 부모 diff 그대로 복사(제목/요약만 바꾼 파생), non-null 이면 통째 교체.

---

## 4. 스키마 변경 (V2 마이그레이션)

`personal_` 접두 테이블 **3개** = 부모 1 + 자식 2:

```
personal_recipe_versions               (부모: 버전 메타, 1 row/버전)
  ├─ personal_ingredient_adjustments   (자식: 재료 diff, N row/버전)
  └─ personal_step_adjustments         (자식: 단계 diff, N row/버전)
```

**왜 3개인가:**
- 버전 vs 조정 분리 → 버전 1 : 조정 N (한 버전에 조정 여러 개)
- 재료 diff vs 단계 diff 분리 → 컬럼·FK·CHECK 가 서로 다른 엔티티 (재료: amount/unit / 단계: instruction/timer/앵커). 합치면 절반 NULL 희소테이블 + FK/CHECK 무력화

**데이터 정합성:**
- `ADD` 는 원본참조 금지 + 필수값 보유, `MODIFY`/`REMOVE` 는 원본참조 필수 — **DB CHECK 로 강제**(앱 버그 있어도 DB 층 최후 방어)
- `ON DELETE CASCADE` 로 버전 삭제 시 diff 고아 방지

그 외:
- `ALTER TABLE personal_recipe_versions DROP COLUMN adjustment_payload`
- 시드: 목 유저 + 데모 레시피 2종(라면/김치볶음밥). 인메모리 픽스처와 **동일 고정 UUID** 로 삽입 → 개인 버전/리뷰 FK 가 실제 행을 가리키게 하는 다리
- **멱등성 처리됨**: `CREATE TABLE/INDEX IF NOT EXISTS`, `DROP COLUMN IF EXISTS`, INSERT `ON CONFLICT (id) DO NOTHING` (로컬 `flyway clean` 재현 안전)

전체 DDL 은 `docs/schema-current.md` 참조.

---

## 5. API 응답 형태

```
GET /api/v1/personal-versions/{id}
{
  "version": { "id", "recipeId", "versionNumber", "parentVersionId", "sourceReviewId", "isDefault", ... },
  "ingredients": [ { "name", "amount", "unit", "origin": "MODIFIED", ... } ],  // 그대로 렌더링
  "steps":       [ { "stepIndex", "instruction", "origin": "ORIGINAL", ... } ],
  "ingredientAdjustments": [ { "type": "MODIFY", "originalIngredientId", "amount" } ],  // 뭐가 바뀌었나
  "stepAdjustments":       [ { "type": "ADD", "insertAfterStepIndex", "instruction" } ]
}
```
`ingredients`/`steps` = 합성 결과(프론트가 그냥 그림). `*Adjustments` = 원시 diff("무엇이 바뀜" 표시용). 이중 용도 의도적 설계.

---

## 6. 계층 구조

| 타입 | 관심사 |
|------|--------|
| `Personal*AdjustmentEntity` | 영속 (JPA, 가변) |
| `IngredientAdjustment` / `StepAdjustment` (record) | API 노출 + 파생 요청 계약 (불변). 응답·요청 양쪽 재사용 → 대칭 계약 |
| `Composed*` (record) | 렌더용 결과 + `origin`(ORIGINAL/MODIFIED/ADDED) |
| `PersonalRecipeVersionDetail` | 메타 + 합성결과 + 원시 diff 묶음 (프론트/AI 단일 계약) |

엔티티가 컨트롤러로 새지 않는다.

---

## 7. 알려진 약점 · 후속 과제

| # | 약점 | 언제 문제 |
|---|------|-----------|
| S1 | `findDetailById` fan-out (버전당 ~4쿼리). 목록서 여러 버전 합성 미리보기 시 ×버전수 | 목록 합성 미리보기 필요해질 때 → 배치/조인 |
| S2 | **원본 불변성 암묵 의존**. 원본 재료 수정/삭제 시 MODIFY/REMOVE 대상·앵커 흔들림. 원본 버저닝/스냅샷 없음 | 원본 편집 기능 생기면 → **실질 리스크 1순위** |
| S3 | **diff-set 행간 모순 무방비**. 같은 원본에 MODIFY+REMOVE 동시/이중 MODIFY 를 DB·서비스 둘 다 안 막음. `byOriginal` Map 이 조용히 덮어씀 | 클라가 모순 diff 보내면 비결정 결과 → set 무결성 검증층 필요 |
| S4 | `sort_order` tie-break 규칙 없음 (동값 시 순서 불안정) | ADD 여러 개 같은 sort_order |
| S5 | 검증 규칙이 Java(`validate*`) + SQL CHECK 이중 복제 → 한쪽만 바뀌면 드리프트 | 규칙 변경 시 |
| S6 | 원본 조회 아직 인메모리 `RecipeService`, V2 시드가 동일 UUID 수동 동기화 | 픽스처 수정 시 시드도 함께(드리프트 부채, 알려진 후속) |

**중복(3장) 스케일 판단**: row 카운트로 세지 말고 신호로 — (1) 버전당 조정 30개 상시 초과 = 데이터 아닌 제품 재검토, (2) 버전 조회 p95 상승 시 구조공유(join 테이블) 검토. MVP 규모선 안 옴.

**테스트 격리**: `PostgresApiTestBase` 는 싱글턴 컨테이너 + 컨텍스트 공유라 클래스 간 데이터가 남는다(주석에 문서화된 의도). 보완책 = 관대한 단언(`greaterThanOrEqualTo`) + 레시피 클래스별 분리(ReviewFlow=김치볶음밥, Derive=라면). `@Transactional` 롤백 미적용 — 현재 단언이 전부 관대/불변시드 기반이라 순서 의존 안 깨지나, 격리 강화하려면 추가 검토 대상.
