# feat/ingredients-table — 재료 정규화 (#70)

## 무엇을 왜

재료 이름이 `recipe_ingredients`(레시피별)와 `personal_ingredient_adjustments`(개인 diff의
ADD/MODIFY)에 행마다 TEXT 로 저장되어, 같은 재료("양파")가 쓰인 곳 수만큼 문자열이 증식했다
(#70 "레시피_재료 라는 말도안되는 테이블 없애기"). 마스터 테이블 `ingredients (id, name UNIQUE)`
를 신설해 이름은 마스터에만 1행 두고, 두 테이블은 `ingredient_id` FK 로만 참조하게 했다.

`recipe_ingredients` 테이블 자체는 없앨 수 없다 — 양/단위/필수 여부/순서는 레시피마다 다른
값이라 연결 테이블로 남아야 한다. "없애기"의 실체는 name 중복 제거다.

## 핵심 설계 결정

- **마스터는 `(id, name)` 뿐.** 레시피별 값은 전부 `recipe_ingredients` 에 남는다. 분류/이미지/
  동의어가 필요해지면 마스터에 컬럼을 추가하면 된다.
- **API 계약 무변경.** 클라이언트/AI 는 지금처럼 name 문자열을 주고받는다. 서버가 저장 시
  get-or-create(`IngredientRepository.findByName` → 없으면 INSERT)로 FK 를 매핑하고, 조회 시
  join 으로 이름을 되돌린다. 엔티티의 `getName()` 위임 메서드 덕에 `RecipeService`/
  `DiffComposer`/추천 로직은 손대지 않았다.
- **diff 모델 유지.** `personal_ingredient_adjustments` 에서 ADD 는 `ingredient_id` 필수,
  MODIFY 는 재료 교체 오버라이드(NULL = 원본 유지), REMOVE 는 안 쓴다. V2 의 이름 없는
  CHECK(ADD 는 name 필수)를 `chk_pia_type_refs`(ADD 는 ingredient_id 필수)로 교체했다.
- **이름 정규화는 스코프 밖.** trim/동의어 통합은 후속 이슈. 지금은 정확히 같은 문자열만
  같은 마스터 행을 공유한다.

## 스키마/API 변경

- `V19__normalize_ingredients.sql`
  - `ingredients (id UUID PK, name TEXT NOT NULL UNIQUE)` 신설
  - 두 테이블의 기존 이름을 UNION-dedup 백필
  - `recipe_ingredients`: `ingredient_id` FK 추가(NOT NULL) 후 `name` 삭제
  - `personal_ingredient_adjustments`: `ingredient_id` FK 추가 후 `name` 삭제, CHECK 교체
- API 변경 없음 (요청/응답 모두 기존 name 문자열 그대로 — openapi.json 재생성 불필요)
- 이슈 댓글은 "V14까지"였지만 main 이 그 사이 V14(태그)~V18(출처 컬럼)을 써서 V19 가 됐다(#77 리뷰 합의 번호).

## 알려진 약점·후속

- ADD 로 들어오는 사용자 입력 이름이 그대로 마스터에 쌓인다 — "양파"/"양파 " 는 별개 행.
  trim + 동의어 통합은 후속 이슈로.
- get-or-create 는 UNIQUE 제약에 기대며 동시 생성 레이스는 별도 처리하지 않았다(충돌 시
  한쪽이 예외 — MVP 트래픽에선 무시).
- 마스터 행 삭제 API 는 없다. FK 가 NO ACTION 이라 참조 중인 행은 어차피 못 지운다.
