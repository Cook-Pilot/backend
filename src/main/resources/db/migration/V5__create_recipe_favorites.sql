-- 사용자별 레시피 즐겨찾기.
-- 같은 사용자가 같은 레시피를 중복 저장하지 못하도록 복합 UNIQUE 제약을 둔다.
CREATE TABLE recipe_favorites (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipe_id  UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, recipe_id)
);

CREATE INDEX idx_recipe_favorites_user_created
  ON recipe_favorites(user_id, created_at DESC);
