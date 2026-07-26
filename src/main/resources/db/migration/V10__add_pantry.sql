-- F-12: 장보기·냉장고 재료 기반 소진 레시피 추천.
--
-- 별도 재료 마스터 테이블이 없어 recipe_ingredients.name과 동일한 표기를
-- 카탈로그 name으로 써서 매칭한다. 카탈로그는 이모티콘 빠른 담기 UI용으로,
-- 현재 8개 카탈로그 레시피에 실제 쓰이는 재료만 curate했다(물은 소진 관리
-- 대상이 아니라 제외).
CREATE TABLE pantry_ingredient_catalog (
  name                     TEXT PRIMARY KEY,
  emoji                    TEXT NOT NULL,
  default_shelf_life_days INT NOT NULL CHECK (default_shelf_life_days > 0),
  sort_order               INT NOT NULL DEFAULT 0
);

-- 사용자가 실제로 담아둔 재료. 같은 재료를 다시 담으면 소진 예정일만
-- 갱신한다(user_id, ingredient_name UNIQUE) — 배치를 여러 줄로 나눠 추적하지 않는다.
CREATE TABLE pantry_items (
  id              UUID PRIMARY KEY,
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  ingredient_name TEXT NOT NULL REFERENCES pantry_ingredient_catalog(name),
  use_by_date     DATE NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, ingredient_name)
);

CREATE INDEX idx_pantry_items_user ON pantry_items(user_id, use_by_date ASC);

INSERT INTO pantry_ingredient_catalog (name, emoji, default_shelf_life_days, sort_order) VALUES
  ('계란', '🥚', 21, 0),
  ('두부', '⬜', 7, 1),
  ('김치', '🫙', 60, 2),
  ('밥', '🍚', 3, 3),
  ('대파', '🌿', 10, 4),
  ('파', '🌿', 10, 5),
  ('양파', '🧅', 30, 6),
  ('마늘', '🧄', 30, 7),
  ('애호박', '🥒', 7, 8),
  ('양배추', '🥬', 14, 9),
  ('고구마', '🍠', 21, 10),
  ('돼지고기 앞다리살', '🥩', 3, 11),
  ('닭다리살', '🍗', 3, 12),
  ('된장', '🍲', 365, 13),
  ('고추장', '🌶️', 365, 14),
  ('고춧가루', '🌶️', 365, 15),
  ('진간장', '🍶', 365, 16),
  ('식용유', '🫒', 365, 17),
  ('올리브유', '🫒', 365, 18),
  ('소금', '🧂', 1000, 19),
  ('라면', '🍜', 180, 20),
  ('스파게티면', '🍝', 365, 21),
  ('페페론치노', '🌶️', 365, 22)
ON CONFLICT (name) DO NOTHING;
