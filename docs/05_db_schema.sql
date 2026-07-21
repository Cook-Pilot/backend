-- 05. CookPilot DB Schema Draft
-- 작성 기준: 2026-07-10 확정 MVP
-- 갱신: 조리 세션 서버 저장 폐기(2026-07-21). 세션 진행(단계 이동/타이머/이벤트)은 프론트가
--   로컬에서 관리하고, 서버에는 조리 결과(리뷰)와 그로부터 파생된 개인 버전만 남긴다.
--   따라서 cook_sessions / cook_session_events / cook_timers 테이블은 두지 않는다.
--   그룹 B(AI) 로그 테이블은 세션 FK 대신 프론트가 생성한 cook_run_id(UUID, FK 없음)로
--   같은 조리 1회의 로그를 묶는다.
-- 목적: 조리 화면 한정 음성 조리 모드, 개인 레시피 버전 저장

CREATE TABLE users (
  id UUID PRIMARY KEY,
  email TEXT UNIQUE,
  display_name TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipes (
  id UUID PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT,
  base_servings NUMERIC(4, 2) NOT NULL DEFAULT 1,
  status TEXT NOT NULL DEFAULT 'active',
  image_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipe_ingredients (
  id UUID PRIMARY KEY,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  amount NUMERIC(10, 2),
  unit TEXT,
  is_required BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE recipe_steps (
  id UUID PRIMARY KEY,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  step_index INT NOT NULL,
  instruction TEXT NOT NULL,
  timer_seconds INT,
  caution_note TEXT,
  image_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (recipe_id, step_index)
);

CREATE TABLE post_cook_reviews (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE RESTRICT,
  rating INT CHECK (rating BETWEEN 1 AND 5),
  comment TEXT,
  next_time_note TEXT,
  structured_feedback JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE personal_recipe_versions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  version_number INT NOT NULL,
  title TEXT NOT NULL,
  summary TEXT,
  adjustment_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  source_review_id UUID REFERENCES post_cook_reviews(id) ON DELETE SET NULL,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, recipe_id, version_number)
);

-- 이하 그룹 B(AI 파트 확정 후 추가). cook_run_id = 프론트가 조리 시작 시 만든 실행 식별자(FK 없음).
CREATE TABLE voice_transcripts (
  id UUID PRIMARY KEY,
  cook_run_id UUID NOT NULL,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  step_index INT,
  transcript TEXT NOT NULL,
  stt_provider TEXT,
  confidence NUMERIC(5, 4),
  routed_intent TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_interactions (
  id UUID PRIMARY KEY,
  cook_run_id UUID NOT NULL,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  voice_transcript_id UUID REFERENCES voice_transcripts(id) ON DELETE SET NULL,
  step_index INT,
  model TEXT,
  user_message TEXT NOT NULL,
  context_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  response_text TEXT NOT NULL,
  action_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tts_events (
  id UUID PRIMARY KEY,
  cook_run_id UUID NOT NULL,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  step_index INT,
  text TEXT NOT NULL,
  reason TEXT NOT NULL,
  provider TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipe_steps_recipe ON recipe_steps(recipe_id, step_index);
CREATE INDEX idx_personal_versions_user_recipe ON personal_recipe_versions(user_id, recipe_id);
CREATE INDEX idx_reviews_recipe_user ON post_cook_reviews(recipe_id, user_id, created_at DESC);
CREATE INDEX idx_voice_transcripts_run ON voice_transcripts(cook_run_id, created_at);
CREATE INDEX idx_ai_interactions_run ON ai_interactions(cook_run_id, created_at);
