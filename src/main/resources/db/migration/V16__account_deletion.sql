-- 계정 삭제(#79). 개인정보처리방침 제4조가 "탈퇴 시 후기 본문·사진 파일까지 함께 삭제되며
-- 익명 상태로 보존하지 않습니다"를 약속하는데, V1 의 ON DELETE SET NULL 은 정반대로
-- 후기를 익명 행으로 남긴다. "익명 통계로 남긴다"는 결정을 한 적이 없으므로 CASCADE 로 바꾼다.
ALTER TABLE post_cook_reviews
  DROP CONSTRAINT post_cook_reviews_user_id_fkey;
ALTER TABLE post_cook_reviews
  ADD CONSTRAINT post_cook_reviews_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- 탈퇴 기록(tombstone). 방침 제8조 "백업 복원 시 이미 탈퇴한 이용자의 정보가 되살아나지
-- 않도록 삭제 처리를 다시 적용한다"를 지키려면 무엇을 지웠는지가 남아 있어야 한다.
-- user_id 는 연결 고리가 전부 사라진 내부 난수 UUID 라 그 자체로는 개인을 식별하지 않는다.
-- 복원 절차에서의 사용법은 infra/README.md 재해 복구 절차 참고.
CREATE TABLE account_deletions (
  user_id    UUID PRIMARY KEY,
  deleted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
