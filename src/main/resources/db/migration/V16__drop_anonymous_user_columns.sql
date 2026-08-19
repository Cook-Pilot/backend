-- 익명 발급 제거. 신원은 이제 (provider, provider_user_id) 로만 정해진다.
--
-- 익명 사용자 행(16명)은 전원 팀 내부 테스트 계정으로 확인되어(2026-08-19) 함께 지운다.
-- 남길 이유였던 "실사용자 기록 보존"이 전제부터 성립하지 않는다.
--
-- 삭제는 is_anonymous 컬럼이 살아 있는 동안 해야 한다. 컬럼이 사라진 뒤에는
-- provider IS NULL 로만 가려낼 수 있는데, 그 조건은 시드 데모 사용자(V2)도 잡아
-- 테스트 기반이 함께 지워진다. 그래서 이 파일 안에서 삭제 → 드랍 순서로 묶는다.

-- 후기를 먼저 지운다. post_cook_reviews.user_id 는 ON DELETE SET NULL 이라
-- 유저를 먼저 지우면 주인 없는 후기가 남고, 누구 것이었는지도 못 찾는다.
DELETE FROM post_cook_reviews
 WHERE user_id IN (SELECT id FROM users WHERE is_anonymous);

-- 개인 버전·즐겨찾기·추천 피드백은 users FK 의 ON DELETE CASCADE 로 함께 삭제된다.
-- 새 환경(CI·로컬)은 익명 사용자가 없어 두 DELETE 모두 0행으로 무해하게 지나간다.
DELETE FROM users WHERE is_anonymous;

ALTER TABLE users
  DROP COLUMN is_anonymous,
  DROP COLUMN anonymous_installation_id,
  DROP COLUMN beta_number;

-- 두 UNIQUE 인덱스는 컬럼과 함께 사라진다. 시퀀스는 beta_number 에 OWNED BY 로 묶여 있어
-- 같이 지워지지만, V6 를 거치지 않은 DB 도 있을 수 있어 명시적으로 한 번 더 정리한다.
DROP SEQUENCE IF EXISTS users_beta_number_seq;
