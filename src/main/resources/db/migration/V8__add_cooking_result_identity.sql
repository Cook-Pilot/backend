-- 한 번의 로컬 조리가 네트워크 재시도로 중복 저장되지 않도록 클라이언트 세션을 식별한다.
-- cooked_at은 서버 수신 시각(created_at)이 아니라 실제 조리가 끝난 시각이다.
ALTER TABLE post_cook_reviews
  ADD COLUMN client_session_id UUID,
  ADD COLUMN cooked_at TIMESTAMPTZ,
  ADD COLUMN source_personal_version_id UUID
    REFERENCES personal_recipe_versions(id) ON DELETE SET NULL,
  ADD COLUMN target_servings NUMERIC(4, 2);

UPDATE post_cook_reviews
SET cooked_at = created_at
WHERE cooked_at IS NULL;

ALTER TABLE post_cook_reviews
  ALTER COLUMN cooked_at SET NOT NULL;

CREATE UNIQUE INDEX uq_reviews_user_client_session
  ON post_cook_reviews(user_id, client_session_id)
  WHERE user_id IS NOT NULL AND client_session_id IS NOT NULL;

CREATE INDEX idx_reviews_user_cooked_at
  ON post_cook_reviews(user_id, cooked_at DESC);

CREATE UNIQUE INDEX uq_personal_versions_source_review
  ON personal_recipe_versions(source_review_id)
  WHERE source_review_id IS NOT NULL;
