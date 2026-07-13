-- 05. CookPilot DB Schema Draft
-- 작성 기준: 2026-07-10 확정 MVP
-- 목적: 조리 화면 한정 음성 조리 모드, 세션 이벤트, 개인 레시피 버전 저장

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
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (recipe_id, step_index)
);

CREATE TABLE personal_recipe_versions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  version_number INT NOT NULL,
  title TEXT NOT NULL,
  summary TEXT,
  adjustment_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  source_session_id UUID,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, recipe_id, version_number)
);

CREATE TABLE cook_sessions (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE RESTRICT,
  personal_version_id UUID REFERENCES personal_recipe_versions(id) ON DELETE SET NULL,
  status TEXT NOT NULL DEFAULT 'ready',
  current_step_index INT NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  aborted_at TIMESTAMPTZ,
  setup_snapshot JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE personal_recipe_versions
  ADD CONSTRAINT personal_recipe_versions_source_session_fk
  FOREIGN KEY (source_session_id) REFERENCES cook_sessions(id) ON DELETE SET NULL;

CREATE TABLE cook_timers (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  step_index INT NOT NULL,
  status TEXT NOT NULL DEFAULT 'running',
  duration_seconds INT NOT NULL,
  remaining_seconds INT,
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  paused_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE cook_session_events (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  event_type TEXT NOT NULL,
  step_index INT,
  source TEXT NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE voice_transcripts (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  step_index INT,
  transcript TEXT NOT NULL,
  stt_provider TEXT,
  confidence NUMERIC(5, 4),
  routed_intent TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_interactions (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  voice_transcript_id UUID REFERENCES voice_transcripts(id) ON DELETE SET NULL,
  step_index INT,
  model TEXT,
  user_message TEXT NOT NULL,
  context_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  response_text TEXT NOT NULL,
  action_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE post_cook_reviews (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE RESTRICT,
  rating INT CHECK (rating BETWEEN 1 AND 5),
  comment TEXT,
  next_time_note TEXT,
  structured_feedback JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tts_events (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  step_index INT,
  text TEXT NOT NULL,
  reason TEXT NOT NULL,
  provider TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipe_steps_recipe ON recipe_steps(recipe_id, step_index);
CREATE INDEX idx_personal_versions_user_recipe ON personal_recipe_versions(user_id, recipe_id);
CREATE INDEX idx_cook_sessions_user ON cook_sessions(user_id, created_at DESC);
CREATE INDEX idx_cook_events_session ON cook_session_events(cook_session_id, created_at);
CREATE INDEX idx_voice_transcripts_session ON voice_transcripts(cook_session_id, created_at);
CREATE INDEX idx_ai_interactions_session ON ai_interactions(cook_session_id, created_at);
CREATE INDEX idx_reviews_recipe_user ON post_cook_reviews(recipe_id, user_id, created_at DESC);

