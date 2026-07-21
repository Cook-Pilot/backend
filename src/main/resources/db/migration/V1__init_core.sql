-- V1: CookPilot 코어 스키마 (그룹 A — AI 파트 계약과 무관한 도메인)
-- 근거: docs/05_db_schema.sql 초안 중 순수 도메인 테이블만 확정.
-- 제외(그룹 B, AI 계약 확정 후 V2로 추가): voice_transcripts, ai_interactions, tts_events.
-- 제외(설계 결정): 조리 세션/세션 이벤트. 세션 진행은 프론트가 로컬에서 관리하고
--   결과(리뷰)만 서버로 넘긴다. 따라서 서버에는 세션 상태를 보관하지 않는다.
-- JSONB 컬럼(adjustment_payload, structured_feedback)은 컬럼만 만들고 내부 구조는
--   열어둔다(opaque). AI 계약 확정 시 제약을 덧붙인다.

-- 사용자 계정. 개인 레시피 버전/리뷰의 소유자.
CREATE TABLE users (
  id           UUID PRIMARY KEY,
  email        TEXT UNIQUE,
  display_name TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 기본(원본) 레시피 메타. 재료/단계는 별도 테이블에 두고 여기서 참조한다.
-- image_url: 대표 이미지. 원본 파일은 외부 스토리지에 두고 DB에는 URL만 둔다(NULL = 이미지 없음).
CREATE TABLE recipes (
  id            UUID PRIMARY KEY,
  title         TEXT NOT NULL,
  description   TEXT,
  base_servings NUMERIC(4, 2) NOT NULL DEFAULT 1,
  status        TEXT NOT NULL DEFAULT 'active',
  image_url     TEXT,
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
-- timer_seconds가 있으면 해당 단계에서 자동 타이머가 돈다(타이머 진행 자체는 프론트 담당).
-- image_url: 단계 참고 이미지. 원본은 외부 스토리지, DB에는 URL만(NULL = 이미지 없음).
CREATE TABLE recipe_steps (
  id            UUID PRIMARY KEY,
  recipe_id     UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  step_index    INT NOT NULL,
  instruction   TEXT NOT NULL,
  timer_seconds INT,
  caution_note  TEXT,
  image_url     TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (recipe_id, step_index)
);

-- 조리 후 리뷰. 프론트에서 조리를 끝낸 뒤 넘겨주는 결과물(세션 대신 이것이 조리 1회의 기록).
-- recipe_id는 RESTRICT: 조리 이력 있는 레시피는 삭제 차단.
-- structured_feedback: 리뷰를 AI가 구조화한 결과라 내부 구조는 열어둔다(opaque) — 개인 버전 생성의 입력이 된다.
CREATE TABLE post_cook_reviews (
  id                  UUID PRIMARY KEY,
  user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
  recipe_id           UUID NOT NULL REFERENCES recipes(id) ON DELETE RESTRICT,
  rating              INT CHECK (rating BETWEEN 1 AND 5),
  comment             TEXT,
  next_time_note      TEXT,
  structured_feedback JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 개인 레시피 버전. 조리 후 피드백을 반영한 "내 버전"으로, 원본 레시피를 사용자별로 파생시킨다.
-- (user_id, recipe_id, version_number) 유니크로 버전 이력을 쌓고, is_default가 다음 조리 때 기본 제공될 버전.
-- adjustment_payload: 원본 레시피 대비 조정 diff(원본 기준 누적). 내부 구조는 AI 파트 확정 대상이라 열어둔다(opaque).
-- source_review_id: 이 버전을 만든 리뷰(추적). 리뷰가 지워져도 버전은 남긴다.
-- parent_version_id: 진화 계보. 이 버전이 어느 버전에서 파생됐는지(원본에서 바로면 NULL). 렌더링은 여전히 원본+payload.
CREATE TABLE personal_recipe_versions (
  id                 UUID PRIMARY KEY,
  user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipe_id          UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  version_number     INT NOT NULL,
  title              TEXT NOT NULL,
  summary            TEXT,
  adjustment_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  source_review_id   UUID REFERENCES post_cook_reviews(id) ON DELETE SET NULL,
  parent_version_id  UUID REFERENCES personal_recipe_versions(id) ON DELETE SET NULL,
  is_default         BOOLEAN NOT NULL DEFAULT FALSE,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, recipe_id, version_number)
);

-- 조회 인덱스: 자주 쓰는 접근 경로(레시피별 단계/재료, 사용자별 버전, 레시피별 리뷰).
CREATE INDEX idx_recipe_ingredients_recipe ON recipe_ingredients(recipe_id, sort_order);
CREATE INDEX idx_recipe_steps_recipe      ON recipe_steps(recipe_id, step_index);
CREATE INDEX idx_personal_versions_user_recipe ON personal_recipe_versions(user_id, recipe_id);
CREATE INDEX idx_reviews_recipe_user      ON post_cook_reviews(recipe_id, user_id, created_at DESC);
