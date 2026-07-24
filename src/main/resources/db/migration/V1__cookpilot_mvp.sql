CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE,
  display_name TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title TEXT NOT NULL,
  description TEXT,
  image_key TEXT NOT NULL,
  total_minutes INT NOT NULL,
  difficulty TEXT NOT NULL,
  base_servings NUMERIC(4, 2) NOT NULL DEFAULT 1,
  status TEXT NOT NULL DEFAULT 'active',
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipe_ingredients (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  amount NUMERIC(10, 2) NOT NULL,
  unit TEXT NOT NULL,
  is_required BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE recipe_steps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  step_index INT NOT NULL,
  instruction TEXT NOT NULL,
  timer_seconds INT,
  caution_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (recipe_id, step_index)
);

CREATE TABLE personal_recipe_versions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  recipe_id UUID NOT NULL REFERENCES recipes(id),
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
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  step_index INT NOT NULL,
  status TEXT NOT NULL,
  duration_seconds INT NOT NULL,
  remaining_seconds INT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE cook_session_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  event_type TEXT NOT NULL,
  step_index INT,
  source TEXT NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE voice_transcripts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  step_index INT,
  transcript TEXT NOT NULL,
  stt_provider TEXT,
  confidence NUMERIC(5, 4),
  routed_intent TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_interactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  step_index INT,
  model TEXT,
  user_message TEXT NOT NULL,
  context_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  response_text TEXT NOT NULL,
  action_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE post_cook_reviews (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  recipe_id UUID NOT NULL REFERENCES recipes(id),
  rating INT CHECK (rating BETWEEN 1 AND 5),
  comment TEXT,
  next_time_note TEXT,
  structured_feedback JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipe_steps_recipe ON recipe_steps(recipe_id, step_index);
CREATE INDEX idx_personal_versions_user_recipe ON personal_recipe_versions(user_id, recipe_id);
CREATE INDEX idx_cook_sessions_user ON cook_sessions(user_id, created_at DESC);
CREATE INDEX idx_cook_events_session ON cook_session_events(cook_session_id, created_at);
CREATE INDEX idx_voice_transcripts_session ON voice_transcripts(cook_session_id, created_at);
CREATE INDEX idx_ai_interactions_session ON ai_interactions(cook_session_id, created_at);
CREATE INDEX idx_reviews_recipe_user ON post_cook_reviews(recipe_id, user_id, created_at DESC);

INSERT INTO users (id, email, display_name)
VALUES ('00000000-0000-0000-0000-000000000001', 'demo@cookpilot.local', 'CookPilot 사용자');

INSERT INTO recipes
  (id, title, description, image_key, total_minutes, difficulty, base_servings, status, sort_order)
VALUES
  ('11111111-1111-1111-1111-111111111111', '라면', '물이 끓는 순간부터 면의 익힘까지 음성으로 놓치지 않게 안내해요.', 'ramen', 8, '쉬움', 1, 'active', 1),
  ('22222222-2222-2222-2222-222222222222', '계란볶음밥', '찬밥과 달걀로 빠르게 완성하는 한 그릇 요리예요.', 'egg_rice', 15, '쉬움', 1, 'active', 2),
  ('33333333-3333-3333-3333-333333333333', '두부조림', '짭조름한 양념을 졸여 밥과 곁들이는 두부 반찬이에요.', 'tofu', 18, '보통', 2, 'active', 3);

INSERT INTO recipe_ingredients
  (id, recipe_id, name, amount, unit, is_required, sort_order)
VALUES
  ('a1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', '물', 500, 'ml', TRUE, 1),
  ('a1121111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', '라면', 1, '봉', TRUE, 2),
  ('a1131111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', '분말스프', 1, '봉', TRUE, 3),
  ('b2211111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '밥', 1, '공기', TRUE, 1),
  ('b2221111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '달걀', 2, '개', TRUE, 2),
  ('b2231111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '대파', 0.5, '대', FALSE, 3),
  ('b2241111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '식용유', 1.5, '큰술', TRUE, 4),
  ('b2251111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '간장', 1, '큰술', TRUE, 5),
  ('c3311111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', '두부', 1, '모', TRUE, 1),
  ('c3321111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', '간장', 3, '큰술', TRUE, 2),
  ('c3331111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', '고춧가루', 1, '큰술', TRUE, 3),
  ('c3341111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', '설탕', 1, '작은술', TRUE, 4),
  ('c3351111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', '물', 100, 'ml', TRUE, 5),
  ('c3361111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', '대파', 0.5, '대', FALSE, 6);

INSERT INTO recipe_steps
  (id, recipe_id, step_index, instruction, timer_seconds, caution_note)
VALUES
  ('d1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 0, '냄비에 물 500ml를 넣고 센 불로 끓이세요.', 180, '뜨거운 냄비와 수증기에 주의하세요.'),
  ('d1121111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 1, '물이 끓으면 면과 분말스프를 넣고 면을 가볍게 풀어주세요.', 30, NULL),
  ('d1131111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 2, '면을 한두 번 들어 올리며 중불에서 끓이세요.', 180, '국물이 넘치면 불을 한 단계 낮추세요.'),
  ('d1141111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 3, '불을 끄고 그릇에 옮겨 담으세요.', NULL, '그릇이 뜨거워질 수 있어요.'),
  ('e2211111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 0, '달걀을 그릇에 풀고 대파는 잘게 썰어 준비하세요.', NULL, '칼을 사용할 때 손가락을 안쪽으로 말아주세요.'),
  ('e2221111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 1, '팬에 식용유를 두르고 대파를 중불에서 볶으세요.', 60, '기름이 튀면 불을 낮추세요.'),
  ('e2231111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 2, '풀어둔 달걀을 넣고 절반쯤 익을 때까지 저으세요.', 60, NULL),
  ('e2241111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 3, '밥과 간장을 넣고 밥알이 고르게 풀리도록 볶으세요.', 120, '팬 바닥이 타지 않게 계속 뒤집어주세요.'),
  ('e2251111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 4, '불을 끄고 간을 확인한 뒤 그릇에 담으세요.', NULL, NULL),
  ('f3311111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 0, '두부의 물기를 닦고 먹기 좋은 크기로 써세요.', NULL, '두부가 미끄러우니 천천히 써세요.'),
  ('f3321111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 1, '간장, 고춧가루, 설탕, 물을 섞어 양념장을 만드세요.', NULL, NULL),
  ('f3331111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 2, '팬에 두부를 올리고 중불에서 앞뒤로 노릇하게 구우세요.', 180, '두부를 뒤집을 때 기름이 튈 수 있어요.'),
  ('f3341111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 3, '양념장과 대파를 넣고 약불에서 국물을 끼얹으며 조리세요.', 300, '양념이 타지 않도록 불을 약하게 유지하세요.'),
  ('f3351111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 4, '국물이 자작해지면 불을 끄고 접시에 담으세요.', NULL, NULL);
