-- V2: 개인 레시피 버전의 조정값을 opaque JSONB 에서 관계형 diff 로 전환한다.
--
-- 설계 결정(2026-07-22 확정):
--  - diff 는 항상 "원본 레시피 기준 누적"이다. 어떤 버전이든 원본 + 그 버전의 diff 만으로
--    렌더링이 끝난다(부모 체인 재생 없음). 파생 시 부모 diff 를 복사한 뒤 수정한다.
--  - ADD 는 원본 참조 없이 자기 데이터를 통째로 가진다(원본에 없는 재료/단계 문제 해결).
--    MODIFY/REMOVE 는 원본 행을 FK 로 가리킨다. CHECK 로 조합을 DB 레벨에서 강제한다.
--  - MODIFY 의 NULL 필드는 "원본 값 유지", non-NULL 은 오버라이드.
--  - adjustment_payload 는 폐기한다. 조정값은 정제된 형태로만 들어온다(AI 도 diff 를 뱉는다).

-- 1) 재료 diff
CREATE TABLE IF NOT EXISTS personal_ingredient_adjustments (
  id                     UUID PRIMARY KEY,
  personal_version_id    UUID NOT NULL REFERENCES personal_recipe_versions(id) ON DELETE CASCADE,
  original_ingredient_id UUID REFERENCES recipe_ingredients(id) ON DELETE CASCADE,
  adjustment_type        TEXT NOT NULL CHECK (adjustment_type IN ('ADD', 'MODIFY', 'REMOVE')),
  name                   TEXT,
  amount                 NUMERIC(10, 2),
  unit                   TEXT,
  is_required            BOOLEAN,
  sort_order             INT NOT NULL DEFAULT 0,
  CHECK ( (adjustment_type = 'ADD'  AND original_ingredient_id IS NULL AND name IS NOT NULL)
       OR (adjustment_type <> 'ADD' AND original_ingredient_id IS NOT NULL) )
);

-- 2) 단계 diff. ADD 는 "원본 몇 번 단계 뒤에 끼우는가"를 insert_after_step_index 로 기록한다
--    (-1 = 맨 앞). 같은 앵커에 여러 ADD 가 붙으면 sort_order 로 그 안의 순서를 정한다.
CREATE TABLE IF NOT EXISTS personal_step_adjustments (
  id                      UUID PRIMARY KEY,
  personal_version_id     UUID NOT NULL REFERENCES personal_recipe_versions(id) ON DELETE CASCADE,
  original_step_id        UUID REFERENCES recipe_steps(id) ON DELETE CASCADE,
  adjustment_type         TEXT NOT NULL CHECK (adjustment_type IN ('ADD', 'MODIFY', 'REMOVE')),
  insert_after_step_index INT,
  sort_order              INT NOT NULL DEFAULT 0,
  instruction             TEXT,
  timer_seconds           INT,
  caution_note            TEXT,
  CHECK ( (adjustment_type = 'ADD'  AND original_step_id IS NULL
             AND instruction IS NOT NULL AND insert_after_step_index >= -1)
       OR (adjustment_type <> 'ADD' AND original_step_id IS NOT NULL
             AND insert_after_step_index IS NULL) )
);

CREATE INDEX IF NOT EXISTS idx_pia_version ON personal_ingredient_adjustments(personal_version_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_psa_version ON personal_step_adjustments(personal_version_id, insert_after_step_index, sort_order);

-- 3) opaque payload 폐기. 현재 내용물은 전부 목데이터라 손실 없음.
ALTER TABLE personal_recipe_versions DROP COLUMN IF EXISTS adjustment_payload;

-- 4) 시드: 목 유저 + 데모 레시피 2종.
--    인메모리 RecipeService 픽스처와 동일한 고정 UUID/내용을 DB 에도 넣어,
--    개인 버전/리뷰의 FK(user_id, recipe_id, original_ingredient_id, original_step_id)가
--    실제 행을 가리킬 수 있게 한다. (레시피 조회 API 의 JPA 전환은 별도 이슈.)
INSERT INTO users (id, email, display_name) VALUES
  ('00000000-0000-0000-0000-000000000001', 'demo@cookpilot.app', '데모 사용자')
ON CONFLICT (id) DO NOTHING;

INSERT INTO recipes (id, title, description, base_servings) VALUES
  ('10000000-0000-0000-0000-000000000001', '라면', 'MVP 데모 시나리오용 기본 라면 레시피', 1),
  ('10000000-0000-0000-0000-000000000002', '김치볶음밥', '기본 김치볶음밥 레시피', 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO recipe_ingredients (id, recipe_id, name, amount, unit, is_required, sort_order) VALUES
  ('20000000-0000-0000-0000-000000000101', '10000000-0000-0000-0000-000000000001', '라면', 1,    '봉',   TRUE,  0),
  ('20000000-0000-0000-0000-000000000102', '10000000-0000-0000-0000-000000000001', '물',   500,  'ml',  TRUE,  1),
  ('20000000-0000-0000-0000-000000000103', '10000000-0000-0000-0000-000000000001', '계란', 1,    '개',   FALSE, 2),
  ('20000000-0000-0000-0000-000000000104', '10000000-0000-0000-0000-000000000001', '파',   0.5,  '대',   FALSE, 3),
  ('20000000-0000-0000-0000-000000000201', '10000000-0000-0000-0000-000000000002', '밥',   1,    '공기', TRUE,  0),
  ('20000000-0000-0000-0000-000000000202', '10000000-0000-0000-0000-000000000002', '김치', 100,  'g',   TRUE,  1),
  ('20000000-0000-0000-0000-000000000203', '10000000-0000-0000-0000-000000000002', '식용유', 1,  '큰술', TRUE,  2),
  ('20000000-0000-0000-0000-000000000204', '10000000-0000-0000-0000-000000000002', '계란', 1,    '개',   FALSE, 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO recipe_steps (id, recipe_id, step_index, instruction, timer_seconds, caution_note) VALUES
  ('30000000-0000-0000-0000-000000000101', '10000000-0000-0000-0000-000000000001', 0, '물 500ml를 넣고 3분간 끓이세요.', 180, NULL),
  ('30000000-0000-0000-0000-000000000102', '10000000-0000-0000-0000-000000000001', 1, '건더기, 분말스프, 면을 넣고 3분간 끓이세요.', 180, '끓어 넘침 주의'),
  ('30000000-0000-0000-0000-000000000103', '10000000-0000-0000-0000-000000000001', 2, '불을 끄고 그릇에 옮겨 담으세요.', NULL, '뜨거우니 조심하세요'),
  ('30000000-0000-0000-0000-000000000201', '10000000-0000-0000-0000-000000000002', 0, '팬에 기름을 두르고 중불로 달구세요.', 60, '기름이 튈 수 있어요'),
  ('30000000-0000-0000-0000-000000000202', '10000000-0000-0000-0000-000000000002', 1, '김치를 넣고 2분간 볶으세요.', 120, NULL),
  ('30000000-0000-0000-0000-000000000203', '10000000-0000-0000-0000-000000000002', 2, '밥을 넣고 3분간 볶으세요.', 180, NULL),
  ('30000000-0000-0000-0000-000000000204', '10000000-0000-0000-0000-000000000002', 3, '불을 끄고 그릇에 담으세요.', NULL, NULL)
ON CONFLICT (id) DO NOTHING;
