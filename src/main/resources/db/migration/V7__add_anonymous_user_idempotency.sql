-- 익명 사용자 생성 응답이 유실되어도 같은 설치 요청은 같은 사용자를 반환한다.
ALTER TABLE users
  ADD COLUMN anonymous_installation_id UUID;

CREATE UNIQUE INDEX uq_users_anonymous_installation_id
  ON users(anonymous_installation_id)
  WHERE anonymous_installation_id IS NOT NULL;
