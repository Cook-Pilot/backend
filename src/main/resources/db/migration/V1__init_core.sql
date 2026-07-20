-- V1: CookPilot 코어 스키마 (그룹 A — AI 파트 계약과 무관한 도메인)
-- 근거: docs/05_db_schema.sql 초안 중 순수 도메인 테이블만 확정.
-- 제외(그룹 B, AI 계약 확정 후 V2로 추가): voice_transcripts, ai_interactions, tts_events.
-- JSONB 컬럼(adjustment_payload, setup_snapshot, payload, structured_feedback)은
-- 컬럼만 만들고 내부 구조는 열어둔다(opaque). AI 계약 확정 시 제약을 덧붙인다.

-- 사용자 계정. 개인 레시피 버전/세션/리뷰의 소유자.
CREATE TABLE users (
  id           UUID PRIMARY KEY,
  email        TEXT UNIQUE,
  display_name TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 기본(원본) 레시피 메타. 재료/단계는 별도 테이블에 두고 여기서 참조한다.
CREATE TABLE recipes (
  id            UUID PRIMARY KEY,
  title         TEXT NOT NULL,
  description   TEXT,
  base_servings NUMERIC(4, 2) NOT NULL DEFAULT 1,
  status        TEXT NOT NULL DEFAULT 'active',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 레시피 재료. recipe_id로 레시피에 종속(레시피 삭제 시 함께 삭제). sort_order로 표시 순서 유지.
CREATE TABLE recipe_ingredients (
  id          UUID PRIMARY KEY,
  recipe_id   UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  amount      NUMERIC(10, 2),
  unit        TEXT,
  is_required BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order  INT NOT NULL DEFAULT 0
);

-- 레시피 조리 단계. step_index 순서로 진행하며 (recipe_id, step_index) 유니크로 중복 단계 방지.
-- timer_seconds가 있으면 해당 단계에서 자동 타이머가 돈다.
CREATE TABLE recipe_steps (
  id            UUID PRIMARY KEY,
  recipe_id     UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  step_index    INT NOT NULL,
  instruction   TEXT NOT NULL,
  timer_seconds INT,
  caution_note  TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (recipe_id, step_index)
);

-- 개인 레시피 버전. 조리 후 피드백을 반영한 "내 버전"으로, 원본 레시피를 사용자별로 파생시킨다.
-- (user_id, recipe_id, version_number) 유니크로 버전 이력을 쌓고, is_default가 다음 조리 때 기본 제공될 버전.
-- adjustment_payload: 원본 레시피 대비 조정 diff(원본 기준 누적). 내부 구조는 AI 파트 확정 대상이라 열어둔다(opaque).
-- parent_version_id: 진화 계보. 이 버전이 어느 버전에서 파생됐는지(원본에서 바로면 NULL). 렌더링은 여전히 원본+payload.
CREATE TABLE personal_recipe_versions (
  id                 UUID PRIMARY KEY,
  user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipe_id          UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  version_number     INT NOT NULL,
  title              TEXT NOT NULL,
  summary            TEXT,
  adjustment_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  source_session_id  UUID,
  parent_version_id  UUID REFERENCES personal_recipe_versions(id) ON DELETE SET NULL,
  is_default         BOOLEAN NOT NULL DEFAULT FALSE,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, recipe_id, version_number)
);

-- 조리 세션. 한 번의 조리 진행 상태(현재 단계/시작·완료·중단 시각)를 담는 핵심 테이블.
-- recipe_id는 필수(RESTRICT: 조리 이력 있는 레시피는 삭제 차단), user/personal_version은 선택.
-- setup_snapshot: 조리 시작 시점의 클라이언트 셋업 스냅샷(opaque).
CREATE TABLE cook_sessions (
  id                  UUID PRIMARY KEY,
  user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
  recipe_id           UUID NOT NULL REFERENCES recipes(id) ON DELETE RESTRICT,
  personal_version_id UUID REFERENCES personal_recipe_versions(id) ON DELETE SET NULL,
  status              TEXT NOT NULL DEFAULT 'ready',
  current_step_index  INT NOT NULL DEFAULT 0,
  started_at          TIMESTAMPTZ,
  completed_at        TIMESTAMPTZ,
  aborted_at          TIMESTAMPTZ,
  setup_snapshot      JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 순환 참조 해소: personal_recipe_versions.source_session_id -> cook_sessions
-- (cook_sessions가 personal_recipe_versions를 참조하므로 테이블 생성 후 FK를 건다.)
ALTER TABLE personal_recipe_versions
  ADD CONSTRAINT personal_recipe_versions_source_session_fk
  FOREIGN KEY (source_session_id) REFERENCES cook_sessions(id) ON DELETE SET NULL;

-- 세션 이벤트 로그(append-only). 단계 이동/타이머/음성명령 등 세션 중 발생한 일을 시간순으로 남긴다.
-- source=이벤트 출처(local/voice 등), payload=이벤트별 부가정보(opaque).
CREATE TABLE cook_session_events (
  id              UUID PRIMARY KEY,
  cook_session_id UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  event_type      TEXT NOT NULL,
  step_index      INT,
  source          TEXT NOT NULL,
  payload         JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 조리 후 리뷰. 세션 종료 후 사용자가 남기는 평가(rating/comment/next_time_note).
-- structured_feedback: 리뷰를 AI가 구조화한 결과라 내부 구조는 열어둔다(opaque) — 개인 버전 생성의 입력이 된다.
CREATE TABLE post_cook_reviews (
  id                  UUID PRIMARY KEY,
  cook_session_id     UUID NOT NULL REFERENCES cook_sessions(id) ON DELETE CASCADE,
  user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
  recipe_id           UUID NOT NULL REFERENCES recipes(id) ON DELETE RESTRICT,
  rating              INT CHECK (rating BETWEEN 1 AND 5),
  comment             TEXT,
  next_time_note      TEXT,
  structured_feedback JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 조회 인덱스: 자주 쓰는 접근 경로(레시피별 단계/재료, 사용자별 세션, 세션별 이벤트, 레시피별 리뷰).
CREATE INDEX idx_recipe_ingredients_recipe ON recipe_ingredients(recipe_id, sort_order);
CREATE INDEX idx_recipe_steps_recipe      ON recipe_steps(recipe_id, step_index);
CREATE INDEX idx_personal_versions_user_recipe ON personal_recipe_versions(user_id, recipe_id);
CREATE INDEX idx_cook_sessions_user       ON cook_sessions(user_id, created_at DESC);
CREATE INDEX idx_cook_events_session      ON cook_session_events(cook_session_id, created_at);
CREATE INDEX idx_reviews_recipe_user      ON post_cook_reviews(recipe_id, user_id, created_at DESC);
