-- 성별·연령대 수집(온보딩 선택 항목). 소셜 제공자에서 받지 않고 앱에서 직접 묻는다.
--
-- profile_asked_at: 온보딩을 보여준 시각. NULL 이면 아직 안 물어봤다.
-- 건너뛰어도 찍힌다 — "건너뛴 사람"과 "안 물어본 사람"을 구분해 재질문을 막는다.

ALTER TABLE users
  ADD COLUMN gender           TEXT,
  ADD COLUMN age_group        INTEGER,
  ADD COLUMN profile_asked_at TIMESTAMPTZ;

ALTER TABLE users
  ADD CONSTRAINT users_gender_check CHECK (gender IN ('M', 'F', 'N')),
  -- 60 = 60세 이상.
  ADD CONSTRAINT users_age_group_check CHECK (age_group IN (10, 20, 30, 40, 50, 60));
