-- 소셜 로그인(#56). 익명 발급을 대체한다.
--
-- 익명 관련 컬럼(is_anonymous, anonymous_installation_id, beta_number)은 여기서 지우지 않는다.
-- 프론트가 아직 익명 발급으로 로그인하므로, 전환이 끝난 뒤 별도 마이그레이션으로 제거한다.

ALTER TABLE users
  ADD COLUMN provider         TEXT,
  ADD COLUMN provider_user_id TEXT;

-- 같은 제공자 안에서 계정 식별자는 유일하다. 제공자가 다르면 같은 식별자여도 다른 사람이다.
CREATE UNIQUE INDEX uq_users_provider_identity
  ON users(provider, provider_user_id);

-- 둘 다 있거나 둘 다 없거나. (익명 사용자는 둘 다 NULL)
ALTER TABLE users
  ADD CONSTRAINT users_provider_identity_check CHECK (
    (provider IS NULL AND provider_user_id IS NULL)
    OR (provider IS NOT NULL AND provider_user_id IS NOT NULL)
  );

-- 이메일 유일 제약을 푼다. 같은 사람이 카카오와 구글로 각각 가입하면 이메일이 겹치는데,
-- 지금은 서로 다른 계정으로 취급한다(계정 통합은 별도 기능). 카카오는 이메일 제공이
-- 선택 동의라 NULL 도 흔하다. 신원의 유일성은 위 (provider, provider_user_id) 가 보장한다.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
