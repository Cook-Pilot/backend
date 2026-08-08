-- 리뷰 사진. 원본 파일은 외부 스토리지에 두고 DB에는 URL 배열만 둔다(recipes.image_url과 같은 방식).
-- JSONB 배열 — 순서는 클라이언트가 보낸 배열 순서 그대로. 빈 배열 = 사진 없음.
ALTER TABLE post_cook_reviews
  ADD COLUMN photo_urls JSONB NOT NULL DEFAULT '[]'::jsonb;
