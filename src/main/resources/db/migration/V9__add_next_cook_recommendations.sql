-- 다음 조리 개인화 추천 (#27).
--
-- 레시피를 하나의 클러스터로 고정하지 않고 여러 특성을 함께 저장한다.
-- 현재 카탈로그는 사람이 검수한 프로필로 시작하고, 이후 새 레시피는 같은
-- 고정 taxonomy를 사용하는 분류 파이프라인으로 보완한다.
CREATE TABLE recipe_flavor_profiles (
  recipe_id       UUID PRIMARY KEY REFERENCES recipes(id) ON DELETE CASCADE,
  cuisine         TEXT NOT NULL,
  dish_type       TEXT NOT NULL,
  cooking_methods JSONB NOT NULL DEFAULT '[]'::JSONB,
  sauce_bases     JSONB NOT NULL DEFAULT '[]'::JSONB,
  flavor_tags     JSONB NOT NULL DEFAULT '[]'::JSONB,
  profile_version INT NOT NULL DEFAULT 1 CHECK (profile_version > 0),
  source          TEXT NOT NULL CHECK (source IN ('MANUAL', 'GEMINI', 'FALLBACK')),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 추천은 조리 스냅샷에 적용될 후보일 뿐 원본/개인 버전을 직접 수정하지 않는다.
-- 사용자의 수락·거절·수정 결과만 이 테이블에 남겨 이후 추천 정책을 평가한다.
CREATE TABLE recommendation_feedback (
  id                     UUID PRIMARY KEY,
  recommendation_id      UUID NOT NULL,
  user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipe_id              UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  original_ingredient_id UUID REFERENCES recipe_ingredients(id) ON DELETE SET NULL,
  decision               TEXT NOT NULL CHECK (decision IN ('ACCEPTED', 'REJECTED', 'MODIFIED')),
  original_amount        NUMERIC(10, 2),
  suggested_amount       NUMERIC(10, 2),
  applied_amount         NUMERIC(10, 2),
  unit                   TEXT,
  reason                 TEXT,
  explanation_source     TEXT NOT NULL CHECK (explanation_source IN ('GEMINI', 'FALLBACK')),
  model                   TEXT,
  prompt_version          TEXT NOT NULL,
  evidence_review_ids    JSONB NOT NULL DEFAULT '[]'::JSONB,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, recommendation_id)
);

CREATE INDEX idx_recommendation_feedback_user_recipe
  ON recommendation_feedback(user_id, recipe_id, created_at DESC);

INSERT INTO recipe_flavor_profiles (
  recipe_id, cuisine, dish_type, cooking_methods, sauce_bases, flavor_tags,
  profile_version, source
) VALUES
  ('10000000-0000-0000-0000-000000000001', 'KOREAN', 'NOODLE',
   '["BOIL"]', '["RAMEN_SOUP"]', '["SALTY", "SPICY", "BROTHY"]', 1, 'MANUAL'),
  ('10000000-0000-0000-0000-000000000002', 'KOREAN', 'RICE',
   '["STIR_FRY"]', '["KIMCHI"]', '["SALTY", "SPICY", "SAVORY"]', 1, 'MANUAL'),
  ('10000000-0000-0000-0000-000000000003', 'KOREAN', 'SIDE_DISH',
   '["PAN_FRY", "BRAISE"]', '["SOY_SAUCE", "CHILI_POWDER"]',
   '["SALTY", "SPICY", "SAVORY"]', 1, 'MANUAL'),
  ('10000000-0000-0000-0000-000000000004', 'KOREAN', 'STEW',
   '["BOIL"]', '["DOENJANG"]', '["SALTY", "SAVORY", "BROTHY"]', 1, 'MANUAL'),
  ('10000000-0000-0000-0000-000000000005', 'KOREAN', 'RICE',
   '["STIR_FRY"]', '["SOY_SAUCE"]', '["SALTY", "SAVORY"]', 1, 'MANUAL'),
  ('10000000-0000-0000-0000-000000000006', 'KOREAN', 'MAIN_DISH',
   '["STIR_FRY"]', '["GOCHUJANG", "SOY_SAUCE"]',
   '["SALTY", "SWEET", "SPICY", "SAVORY"]', 1, 'MANUAL'),
  ('10000000-0000-0000-0000-000000000007', 'WESTERN', 'NOODLE',
   '["BOIL", "STIR_FRY"]', '["OLIVE_OIL", "SALT"]',
   '["SALTY", "SAVORY"]', 1, 'MANUAL'),
  ('10000000-0000-0000-0000-000000000008', 'KOREAN', 'MAIN_DISH',
   '["STIR_FRY"]', '["GOCHUJANG"]',
   '["SALTY", "SWEET", "SPICY", "SAVORY"]', 1, 'MANUAL')
ON CONFLICT (recipe_id) DO NOTHING;
