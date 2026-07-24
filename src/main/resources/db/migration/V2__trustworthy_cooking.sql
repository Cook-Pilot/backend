CREATE TABLE anonymous_installs (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  credential_hash TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  revoked_at TIMESTAMPTZ
);

ALTER TABLE recipes ADD COLUMN content_revision INT NOT NULL DEFAULT 1 CHECK (content_revision > 0);
ALTER TABLE recipe_ingredients ADD COLUMN personalization_role TEXT NOT NULL DEFAULT 'none'
  CHECK (personalization_role IN ('none', 'salty', 'spicy', 'liquid'));
ALTER TABLE recipe_ingredients ADD COLUMN personalization_locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE recipe_steps ADD COLUMN timer_adjustable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE recipe_steps ADD COLUMN timer_min_seconds INT;
ALTER TABLE recipe_steps ADD COLUMN timer_max_seconds INT;
ALTER TABLE recipe_steps ADD COLUMN safety_tier TEXT NOT NULL DEFAULT 'normal'
  CHECK (safety_tier IN ('normal', 'caution', 'blocked'));
ALTER TABLE recipe_steps ADD COLUMN start_confirmation_label TEXT;
ALTER TABLE recipe_steps ADD COLUMN completion_cue TEXT;

ALTER TABLE cook_sessions ADD COLUMN install_id UUID REFERENCES anonymous_installs(id) ON DELETE CASCADE;
ALTER TABLE cook_sessions ADD COLUMN active_install_id UUID REFERENCES anonymous_installs(id) ON DELETE SET NULL;
ALTER TABLE cook_sessions ADD COLUMN revision INT NOT NULL DEFAULT 0 CHECK (revision >= 0);
ALTER TABLE cook_sessions ADD COLUMN review_state TEXT NOT NULL DEFAULT 'not_eligible'
  CHECK (review_state IN ('not_eligible', 'open', 'submitted', 'skipped'));
UPDATE cook_sessions SET status = 'aborted', aborted_at = COALESCE(aborted_at, NOW())
WHERE status IN ('ready', 'paused');
CREATE UNIQUE INDEX uq_cook_sessions_active_install ON cook_sessions(active_install_id);
CREATE INDEX idx_cook_sessions_install_created ON cook_sessions(install_id, created_at DESC);

ALTER TABLE post_cook_reviews ADD COLUMN generation_status TEXT NOT NULL DEFAULT 'pending'
  CHECK (generation_status IN ('pending', 'no_change', 'proposal_ready', 'failed_retryable'));
CREATE UNIQUE INDEX uq_post_cook_reviews_session ON post_cook_reviews(cook_session_id);

ALTER TABLE personal_recipe_versions ADD COLUMN install_id UUID REFERENCES anonymous_installs(id) ON DELETE CASCADE;
ALTER TABLE personal_recipe_versions ADD COLUMN parent_version_id UUID REFERENCES personal_recipe_versions(id) ON DELETE SET NULL;
ALTER TABLE personal_recipe_versions ADD COLUMN base_recipe_revision INT NOT NULL DEFAULT 1;

CREATE TABLE personal_recipe_proposals (
  id UUID PRIMARY KEY,
  review_id UUID NOT NULL UNIQUE REFERENCES post_cook_reviews(id) ON DELETE CASCADE,
  install_id UUID NOT NULL REFERENCES anonymous_installs(id) ON DELETE CASCADE,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  parent_version_id UUID REFERENCES personal_recipe_versions(id) ON DELETE SET NULL,
  base_recipe_revision INT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  decided_at TIMESTAMPTZ
);

ALTER TABLE personal_recipe_versions ADD COLUMN source_proposal_id UUID REFERENCES personal_recipe_proposals(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uq_personal_versions_source_proposal ON personal_recipe_versions(source_proposal_id);
CREATE UNIQUE INDEX uq_personal_versions_install_recipe_number
  ON personal_recipe_versions(install_id, recipe_id, version_number);

CREATE TABLE personal_recipe_proposal_items (
  id UUID PRIMARY KEY,
  proposal_id UUID NOT NULL REFERENCES personal_recipe_proposals(id) ON DELETE CASCADE,
  item_key TEXT NOT NULL,
  item_type TEXT NOT NULL CHECK (item_type IN ('ingredient_amount', 'step_timer')),
  label TEXT NOT NULL,
  ingredient_id UUID REFERENCES recipe_ingredients(id),
  step_id UUID REFERENCES recipe_steps(id),
  before_amount NUMERIC(10, 2),
  proposed_amount NUMERIC(10, 2),
  before_seconds INT,
  proposed_seconds INT,
  decision TEXT CHECK (decision IS NULL OR decision IN ('applied', 'kept')),
  UNIQUE (proposal_id, item_key),
  CHECK (
    (item_type = 'ingredient_amount' AND ingredient_id IS NOT NULL AND step_id IS NULL
      AND before_amount IS NOT NULL AND proposed_amount IS NOT NULL
      AND before_seconds IS NULL AND proposed_seconds IS NULL)
    OR
    (item_type = 'step_timer' AND step_id IS NOT NULL AND ingredient_id IS NULL
      AND before_seconds IS NOT NULL AND proposed_seconds IS NOT NULL
      AND before_amount IS NULL AND proposed_amount IS NULL)
  )
);

CREATE TABLE personal_recipe_version_items (
  id UUID PRIMARY KEY,
  version_id UUID NOT NULL REFERENCES personal_recipe_versions(id) ON DELETE CASCADE,
  item_key TEXT NOT NULL,
  item_type TEXT NOT NULL CHECK (item_type IN ('ingredient_amount', 'step_timer')),
  label TEXT NOT NULL,
  ingredient_id UUID REFERENCES recipe_ingredients(id),
  step_id UUID REFERENCES recipe_steps(id),
  effective_amount NUMERIC(10, 2),
  effective_seconds INT,
  UNIQUE (version_id, item_key),
  CHECK (
    (item_type = 'ingredient_amount' AND ingredient_id IS NOT NULL AND step_id IS NULL
      AND effective_amount IS NOT NULL AND effective_seconds IS NULL)
    OR
    (item_type = 'step_timer' AND step_id IS NOT NULL AND ingredient_id IS NULL
      AND effective_seconds IS NOT NULL AND effective_amount IS NULL)
  )
);

CREATE TABLE personal_recipe_defaults (
  install_id UUID NOT NULL REFERENCES anonymous_installs(id) ON DELETE CASCADE,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  default_version_id UUID REFERENCES personal_recipe_versions(id) ON DELETE SET NULL,
  pointer_revision INT NOT NULL DEFAULT 0 CHECK (pointer_revision >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (install_id, recipe_id)
);

CREATE TABLE idempotency_operations (
  install_id UUID NOT NULL REFERENCES anonymous_installs(id) ON DELETE CASCADE,
  idempotency_key UUID NOT NULL,
  operation TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  http_status INT,
  response_body JSONB,
  resource_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (install_id, idempotency_key)
);

ALTER TABLE cook_session_events ADD COLUMN client_event_id UUID;
CREATE UNIQUE INDEX uq_cook_session_events_client ON cook_session_events(cook_session_id, client_event_id);

UPDATE recipe_ingredients SET personalization_role = 'salty'
WHERE id IN ('a1131111-1111-1111-1111-111111111111',
             'b2251111-1111-1111-1111-111111111111',
             'c3321111-1111-1111-1111-111111111111');
UPDATE recipe_ingredients SET personalization_role = 'spicy'
WHERE id = 'c3331111-1111-1111-1111-111111111111';
UPDATE recipe_ingredients SET personalization_role = 'liquid'
WHERE id IN ('a1111111-1111-1111-1111-111111111111',
             'c3351111-1111-1111-1111-111111111111');
UPDATE recipe_steps
SET timer_adjustable = TRUE,
    timer_min_seconds = GREATEST(15, timer_seconds - 120),
    timer_max_seconds = timer_seconds + 120
WHERE timer_seconds IS NOT NULL AND caution_note IS NULL;
UPDATE recipe_steps SET safety_tier = 'caution', timer_adjustable = FALSE WHERE caution_note IS NOT NULL;
UPDATE recipe_steps SET safety_tier = 'blocked', timer_adjustable = FALSE
WHERE id = 'e2231111-1111-1111-1111-111111111111';

UPDATE recipe_steps SET start_confirmation_label = '물이 끓기 시작했어요 · 3분 시작', completion_cue = '물이 힘있게 끓는지 확인'
WHERE id = 'd1111111-1111-1111-1111-111111111111';
UPDATE recipe_steps SET start_confirmation_label = '면과 스프를 넣었어요 · 30초 시작', completion_cue = '면이 풀리기 시작했는지 확인'
WHERE id = 'd1121111-1111-1111-1111-111111111111';
UPDATE recipe_steps SET start_confirmation_label = '면과 스프를 넣었어요 · 3분 시작', completion_cue = '면의 탄력을 확인'
WHERE id = 'd1131111-1111-1111-1111-111111111111';
UPDATE recipe_steps SET start_confirmation_label = '대파를 볶기 시작했어요 · 1분 시작', completion_cue = '파 향이 올라오는지 확인'
WHERE id = 'e2221111-1111-1111-1111-111111111111';
UPDATE recipe_steps SET start_confirmation_label = '달걀을 넣었어요 · 1분 시작', completion_cue = '달걀이 절반쯤 익었는지 확인'
WHERE id = 'e2231111-1111-1111-1111-111111111111';
UPDATE recipe_steps SET start_confirmation_label = '밥과 간장을 넣었어요 · 2분 시작', completion_cue = '밥알이 고르게 풀렸는지 확인'
WHERE id = 'e2241111-1111-1111-1111-111111111111';
UPDATE recipe_steps SET start_confirmation_label = '두부를 굽기 시작했어요 · 3분 시작', completion_cue = '앞뒤가 노릇한지 확인'
WHERE id = 'f3331111-1111-1111-1111-111111111111';
UPDATE recipe_steps SET start_confirmation_label = '양념장을 넣었어요 · 5분 시작', completion_cue = '국물이 자작하고 타지 않는지 확인'
WHERE id = 'f3341111-1111-1111-1111-111111111111';
