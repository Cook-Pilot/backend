-- 익명 발급 제거. 신원은 이제 (provider, provider_user_id) 로만 정해진다.
--
-- 기존 익명 사용자 행은 지우지 않는다. 후기·개인 버전이 FK 로 물고 있어 지우면 그 기록까지
-- 사라진다. provider 가 NULL 이라 다시 로그인할 수는 없는 과거 데이터로 남는다.

ALTER TABLE users
  DROP COLUMN is_anonymous,
  DROP COLUMN anonymous_installation_id,
  DROP COLUMN beta_number;

-- 두 UNIQUE 인덱스는 컬럼과 함께 사라진다. 시퀀스는 beta_number 에 OWNED BY 로 묶여 있어
-- 같이 지워지지만, V6 를 거치지 않은 DB 도 있을 수 있어 명시적으로 한 번 더 정리한다.
DROP SEQUENCE IF EXISTS users_beta_number_seq;
