CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY,
  email VARCHAR(320) UNIQUE,
  display_name VARCHAR(100),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recipes (
  id UUID PRIMARY KEY,
  title VARCHAR(120) NOT NULL,
  description VARCHAR(1000),
  image_key VARCHAR(80) NOT NULL,
  total_minutes INT NOT NULL,
  difficulty VARCHAR(30) NOT NULL,
  base_servings NUMERIC(4, 2) NOT NULL DEFAULT 1,
  status VARCHAR(30) NOT NULL DEFAULT 'active',
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recipe_ingredients (
  id UUID PRIMARY KEY,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  amount NUMERIC(10, 2) NOT NULL,
  unit VARCHAR(30) NOT NULL,
  is_required BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recipe_steps (
  id UUID PRIMARY KEY,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  step_index INT NOT NULL,
  instruction VARCHAR(1000) NOT NULL,
  timer_seconds INT,
  caution_note VARCHAR(500),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (recipe_id, step_index)
);

CREATE TABLE IF NOT EXISTS personal_recipe_versions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  version_number INT NOT NULL,
  title VARCHAR(200) NOT NULL,
  summary VARCHAR(1000),
  adjustment_payload CLOB NOT NULL,
  source_session_id UUID,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id, recipe_id, version_number)
);

CREATE TABLE IF NOT EXISTS cook_sessions (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  recipe_id UUID NOT NULL REFERENCES recipes(id),
  personal_version_id UUID REFERENCES personal_recipe_versions(id) ON DELETE SET NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ready',
  current_step_index INT NOT NULL DEFAULT 0,
  started_at TIMESTAMP WITH TIME ZONE,
  completed_at TIMESTAMP WITH TIME ZONE,
  aborted_at TIMESTAMP WITH TIME ZONE,
  setup_snapshot CLOB NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cook_timers (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  step_index INT NOT NULL,
  status VARCHAR(30) NOT NULL,
  duration_seconds INT NOT NULL,
  remaining_seconds INT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cook_session_events (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  event_type VARCHAR(80) NOT NULL,
  step_index INT,
  source VARCHAR(30) NOT NULL,
  payload CLOB NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS voice_transcripts (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  step_index INT,
  transcript VARCHAR(1000) NOT NULL,
  stt_provider VARCHAR(80),
  confidence NUMERIC(5, 4),
  routed_intent VARCHAR(80),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_interactions (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  step_index INT,
  model VARCHAR(120),
  user_message VARCHAR(1000) NOT NULL,
  context_payload CLOB NOT NULL,
  response_text VARCHAR(2000) NOT NULL,
  action_payload CLOB NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS post_cook_reviews (
  id UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  recipe_id UUID NOT NULL REFERENCES recipes(id),
  rating INT CHECK (rating BETWEEN 1 AND 5),
  comment VARCHAR(2000),
  next_time_note VARCHAR(2000),
  structured_feedback CLOB NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recipe_steps_recipe ON recipe_steps(recipe_id, step_index);
CREATE INDEX IF NOT EXISTS idx_personal_versions_user_recipe ON personal_recipe_versions(user_id, recipe_id);
CREATE INDEX IF NOT EXISTS idx_cook_sessions_user ON cook_sessions(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_cook_events_session ON cook_session_events(cook_session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_voice_transcripts_session ON voice_transcripts(cook_session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_interactions_session ON ai_interactions(cook_session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_reviews_recipe_user ON post_cook_reviews(recipe_id, user_id, created_at);

