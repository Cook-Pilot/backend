-- 실제 인증을 붙이기 전 폐쇄 베타에서 기기별 데이터를 분리하기 위한 익명 사용자 정보.
-- UUID는 기존 FK 관계용 식별자로 유지하고, beta_number는 화면에 표시할 가입 순서만 담당한다.
CREATE SEQUENCE users_beta_number_seq START WITH 1;

ALTER TABLE users
  ADD COLUMN beta_number BIGINT,
  ADD COLUMN is_anonymous BOOLEAN NOT NULL DEFAULT FALSE;

-- 기존 고정 데모 사용자는 베타 참여자 번호에서 제외한다.
UPDATE users
SET beta_number = 0
WHERE id = '00000000-0000-0000-0000-000000000001';

-- 데모 사용자 외 기존 행이 있더라도 각기 다른 번호를 부여한다.
UPDATE users
SET beta_number = nextval('users_beta_number_seq')
WHERE beta_number IS NULL;

ALTER TABLE users
  ALTER COLUMN beta_number SET DEFAULT nextval('users_beta_number_seq'),
  ALTER COLUMN beta_number SET NOT NULL;

ALTER SEQUENCE users_beta_number_seq OWNED BY users.beta_number;

CREATE UNIQUE INDEX uq_users_beta_number ON users(beta_number);
