-- V10의 짧은 ADD CONSTRAINT 잠금과 기존 행 전체 검증을 분리한다.
-- PostgreSQL VALIDATE CONSTRAINT는 일반 읽기·쓰기를 막지 않으며, Flyway는
-- migration 파일마다 트랜잭션을 끝내므로 V10의 ACCESS EXCLUSIVE 잠금을
-- 테이블 스캔 시간만큼 유지하지 않는다.
ALTER TABLE post_cook_reviews
  VALIDATE CONSTRAINT ck_reviews_cooking_result_bundle,
  VALIDATE CONSTRAINT ck_reviews_review_status,
  VALIDATE CONSTRAINT ck_reviews_pending_or_skipped_requires_result,
  VALIDATE CONSTRAINT ck_reviews_non_finalized_review_data_empty;
