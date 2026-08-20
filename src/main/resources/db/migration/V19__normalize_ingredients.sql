-- V19: 재료 정규화 (#70).
-- (번호가 V19 인 이유: 작성 시점엔 V16 이었으나 main 이 그 사이 V16 익명컬럼 정리 ·
--  V17 태그 제약 · V18 출처 컬럼을 선점했다. #77 리뷰에서 합의한 번호다.)
-- 재료 이름이 recipe_ingredients / personal_ingredient_adjustments 행마다 TEXT 로 중복
-- 저장되어 같은 재료("양파")가 쓰인 곳 수만큼 증식하던 문제를 마스터 테이블 ingredients 로
-- 해결한다. 이름은 마스터에만 1행 존재하고, 나머지 테이블은 ingredient_id FK 로만 참조한다.
-- recipe_ingredients 는 레시피별 값(양/단위/필수 여부/순서)만 가진 연결 테이블로 남는다.

CREATE TABLE ingredients (
  id   UUID PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

-- 기존 재료명 백필: 두 테이블의 이름을 모아 중복 제거 후 마스터에 1행씩(UNION 이 dedup).
INSERT INTO ingredients (id, name)
SELECT gen_random_uuid(), name
FROM (
  SELECT name FROM recipe_ingredients
  UNION
  SELECT name FROM personal_ingredient_adjustments WHERE name IS NOT NULL
) AS existing_names;

-- 1) recipe_ingredients: name → ingredient_id FK
ALTER TABLE recipe_ingredients ADD COLUMN ingredient_id UUID REFERENCES ingredients(id);

UPDATE recipe_ingredients ri
SET ingredient_id = i.id
FROM ingredients i
WHERE i.name = ri.name;

ALTER TABLE recipe_ingredients ALTER COLUMN ingredient_id SET NOT NULL;
ALTER TABLE recipe_ingredients DROP COLUMN name;

CREATE INDEX idx_recipe_ingredients_ingredient ON recipe_ingredients(ingredient_id);

-- 2) personal_ingredient_adjustments: name → ingredient_id FK
--    ADD 는 새 재료 지정, MODIFY 는 재료 교체 오버라이드(NULL = 원본 유지), REMOVE 는 안 쓴다.
ALTER TABLE personal_ingredient_adjustments ADD COLUMN ingredient_id UUID REFERENCES ingredients(id);

UPDATE personal_ingredient_adjustments pia
SET ingredient_id = i.id
FROM ingredients i
WHERE i.name = pia.name;

-- V2 의 이름 없는 테이블 CHECK(ADD 는 name 필수)를 ingredient_id 기준으로 교체한다.
-- V17 과 같은 이유로 이름을 추측해 박지 않고 카탈로그에서 찾아 지운다(자동 생성 이름은
-- 환경에 따라 다를 수 있다). 대상 식별은 정의에 name 컬럼이 걸려 있는 CHECK 라는 점 —
-- adjustment_type IN(...) 과 chk_pia_amount_override_present 는 name 을 참조하지 않는다.
DO $migration$
DECLARE
  old_check TEXT;
BEGIN
  SELECT conname INTO STRICT old_check
  FROM pg_constraint
  WHERE conrelid = 'personal_ingredient_adjustments'::regclass
    AND contype = 'c'
    AND pg_get_constraintdef(oid) LIKE '%name IS NOT NULL%';

  EXECUTE format('ALTER TABLE personal_ingredient_adjustments DROP CONSTRAINT %I', old_check);
END
$migration$;
ALTER TABLE personal_ingredient_adjustments
  ADD CONSTRAINT chk_pia_type_refs
  CHECK ( (adjustment_type = 'ADD'  AND original_ingredient_id IS NULL AND ingredient_id IS NOT NULL)
       OR (adjustment_type <> 'ADD' AND original_ingredient_id IS NOT NULL) );

ALTER TABLE personal_ingredient_adjustments DROP COLUMN name;

CREATE INDEX idx_pia_ingredient ON personal_ingredient_adjustments(ingredient_id);
