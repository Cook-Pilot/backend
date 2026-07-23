# CookPilot DB 스키마 (현재 상태)

V1 + V2 마이그레이션 적용 후 최종 스키마. Postgres 16.

> ⚠️ 문서용 통합 뷰다. 실제 적용은 Flyway가 `V1__init_core.sql` → `V2__personal_diff_and_seed.sql` 순차 실행한다. 아래 SQL을 통짜로 실행하면 Flyway 이력과 안 맞으니 참고용으로만 써라.

## 테이블 개요

| 테이블 | 역할 |
|--------|------|
| `users` | 사용자 계정. 개인 버전/리뷰 소유자 |
| `recipes` | 원본 레시피 메타 |
| `recipe_ingredients` | 원본 재료 |
| `recipe_steps` | 원본 단계 |
| `post_cook_reviews` | 조리 후 리뷰. 개인 버전 생성의 입력 |
| `personal_recipe_versions` | 개인 레시피 버전(내 버전). 원본 기준 diff로 렌더 |
| `personal_ingredient_adjustments` | 재료 diff(ADD/MODIFY/REMOVE) |
| `personal_step_adjustments` | 단계 diff(ADD/MODIFY/REMOVE) |

## 핵심 설계

- diff는 **항상 원본 기준 누적** — 버전 렌더 = `원본 + 해당 버전 diff`(부모체인 재생 없음).
- `ADD`는 원본참조 없이 자기 데이터 보유, `MODIFY`/`REMOVE`는 원본 FK 참조 — DB `CHECK`로 강제.
- `MODIFY`: NULL 필드 = 원본 유지, non-NULL = 오버라이드.
- 단계 `ADD`: `insert_after_step_index` 앵커(-1 = 맨 앞).

## DDL

```sql
-- ============================================================
-- CookPilot 현재 DB 스키마 (V1 + V2 적용 후 최종 상태)
-- Postgres 16
-- ============================================================

-- 사용자
CREATE TABLE users (
  id           UUID PRIMARY KEY,
  email        TEXT UNIQUE,
  display_name TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 원본 레시피 메타
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

-- 원본 재료
CREATE TABLE recipe_ingredients (
  id          UUID PRIMARY KEY,
  recipe_id   UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  amount      NUMERIC(10, 2),
  unit        TEXT,
  is_required BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order  INT NOT NULL DEFAULT 0
);

-- 원본 단계
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

-- 조리 후 리뷰 (개인 버전 생성의 입력)
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

-- 개인 레시피 버전 (V2에서 adjustment_payload 컬럼 제거됨)
CREATE TABLE personal_recipe_versions (
  id                UUID PRIMARY KEY,
  user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipe_id         UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  version_number    INT NOT NULL,
  title             TEXT NOT NULL,
  summary           TEXT,
  source_review_id  UUID REFERENCES post_cook_reviews(id) ON DELETE SET NULL,
  parent_version_id UUID REFERENCES personal_recipe_versions(id) ON DELETE SET NULL,
  is_default        BOOLEAN NOT NULL DEFAULT FALSE,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, recipe_id, version_number)
);

-- 재료 diff (원본 대비 조정)
CREATE TABLE personal_ingredient_adjustments (
  id                     UUID PRIMARY KEY,
  personal_version_id    UUID NOT NULL REFERENCES personal_recipe_versions(id) ON DELETE CASCADE,
  original_ingredient_id UUID REFERENCES recipe_ingredients(id) ON DELETE CASCADE,
  adjustment_type        TEXT NOT NULL CHECK (adjustment_type IN ('ADD', 'MODIFY', 'REMOVE')),
  name                   TEXT,
  amount                 NUMERIC(10, 2),
  unit                   TEXT,
  is_required            BOOLEAN,
  sort_order             INT NOT NULL DEFAULT 0,
  CHECK ( (adjustment_type = 'ADD'  AND original_ingredient_id IS NULL AND name IS NOT NULL)
       OR (adjustment_type <> 'ADD' AND original_ingredient_id IS NOT NULL) )
);

-- 단계 diff (ADD는 insert_after_step_index 앵커, -1=맨앞)
CREATE TABLE personal_step_adjustments (
  id                      UUID PRIMARY KEY,
  personal_version_id     UUID NOT NULL REFERENCES personal_recipe_versions(id) ON DELETE CASCADE,
  original_step_id        UUID REFERENCES recipe_steps(id) ON DELETE CASCADE,
  adjustment_type         TEXT NOT NULL CHECK (adjustment_type IN ('ADD', 'MODIFY', 'REMOVE')),
  insert_after_step_index INT,
  sort_order              INT NOT NULL DEFAULT 0,
  instruction             TEXT,
  timer_seconds           INT,
  caution_note            TEXT,
  CHECK ( (adjustment_type = 'ADD'  AND original_step_id IS NULL
             AND instruction IS NOT NULL AND insert_after_step_index >= -1)
       OR (adjustment_type <> 'ADD' AND original_step_id IS NOT NULL
             AND insert_after_step_index IS NULL) )
);

-- 인덱스
CREATE INDEX idx_recipe_ingredients_recipe     ON recipe_ingredients(recipe_id, sort_order);
CREATE INDEX idx_recipe_steps_recipe           ON recipe_steps(recipe_id, step_index);
CREATE INDEX idx_personal_versions_user_recipe ON personal_recipe_versions(user_id, recipe_id);
CREATE INDEX idx_reviews_recipe_user           ON post_cook_reviews(recipe_id, user_id, created_at DESC);
CREATE INDEX idx_pia_version ON personal_ingredient_adjustments(personal_version_id, sort_order);
CREATE INDEX idx_psa_version ON personal_step_adjustments(personal_version_id, insert_after_step_index, sort_order);
```
