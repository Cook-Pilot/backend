-- 사용자별 최근 조리에서 레시피별 최신 리뷰를 제한 조회하는 경로를 지원한다.
CREATE INDEX idx_reviews_user_recipe_created
  ON post_cook_reviews(user_id, recipe_id, created_at DESC, id DESC);
