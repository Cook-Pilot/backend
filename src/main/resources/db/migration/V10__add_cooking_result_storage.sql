-- F-09 조리완료 결과를 후기와 독립적으로 먼저 저장하기 위한 확장 스키마.
--
-- cooking_result_payload는 프론트가 보낸 완료 사실의 불변 정본이다. 개인 레시피
-- diff나 이슈 #34의 setup/cooking/review 파이프라인 입력·결과를 뜻하지 않으며,
-- 서버가 ADD/MODIFY/REMOVE를 추론해 채우지 않는다. 구체 JSON 계약은 후속 API
-- PR에서 Java DTO와 함께 고정한다.
--
-- 기존 행은 완료와 후기가 한 번에 저장된 레거시 기록이므로 payload bundle은
-- NULL로 남기고 review_status만 FINALIZED로 보존한다.
ALTER TABLE post_cook_reviews
  ADD COLUMN cooking_result_schema_version SMALLINT,
  ADD COLUMN cooking_result_payload JSONB,
  ADD COLUMN cooking_result_fingerprint TEXT,
  ADD COLUMN review_status TEXT NOT NULL DEFAULT 'FINALIZED';

COMMENT ON COLUMN post_cook_reviews.cooking_result_schema_version IS
  'Version of the immutable cooking-result JSON contract; NULL for legacy rows';
COMMENT ON COLUMN post_cook_reviews.cooking_result_payload IS
  'Immutable frontend cooking-result facts; never a server-inferred recipe diff';
COMMENT ON COLUMN post_cook_reviews.cooking_result_fingerprint IS
  'Lowercase SHA-256 of the versioned cooking-result projection';
COMMENT ON COLUMN post_cook_reviews.review_status IS
  'PENDING_REVIEW, FINALIZED, or SKIPPED';

ALTER TABLE post_cook_reviews
  ADD CONSTRAINT ck_reviews_cooking_result_bundle
  CHECK (
    (
      cooking_result_schema_version IS NULL
      AND cooking_result_payload IS NULL
      AND cooking_result_fingerprint IS NULL
    )
    OR
    (
      cooking_result_schema_version IS NOT NULL
      AND cooking_result_schema_version = 1
      AND cooking_result_payload IS NOT NULL
      AND jsonb_typeof(cooking_result_payload) = 'object'
      AND cooking_result_fingerprint IS NOT NULL
      AND cooking_result_fingerprint ~ '^[0-9a-f]{64}$'
    )
  ) NOT VALID;
ALTER TABLE post_cook_reviews
  ADD CONSTRAINT ck_reviews_review_status
  CHECK (review_status IN ('PENDING_REVIEW', 'FINALIZED', 'SKIPPED'))
  NOT VALID;

-- 구버전 바이너리와 기존 FINALIZED 행을 위한 expand 단계라 FINALIZED+NULL bundle은
-- 허용한다. 새 완료 API는 앱 계층에서 bundle을 항상 명시하고, PENDING_REVIEW와
-- SKIPPED는 DB에서도 멱등성 검증 가능한 완료 결과를 강제한다.
ALTER TABLE post_cook_reviews
  ADD CONSTRAINT ck_reviews_pending_or_skipped_requires_result
  CHECK (
    review_status = 'FINALIZED'
    OR
    (
      cooking_result_schema_version IS NOT NULL
      AND cooking_result_payload IS NOT NULL
      AND cooking_result_fingerprint IS NOT NULL
    )
  ) NOT VALID;

-- 후기 내용은 FINALIZED 전에는 존재할 수 없다. 특히 SKIPPED는 후기 없이 종료한
-- 상태이므로 후기 필드가 남아 있으면 안 되고, PENDING_REVIEW 역시 완료 사실만
-- 먼저 저장하는 단계다. FINALIZED의 후기 필드는 모두 선택 사항이라 NULL을 허용한다.
ALTER TABLE post_cook_reviews
  ADD CONSTRAINT ck_reviews_non_finalized_review_fields_null
  CHECK (
    review_status = 'FINALIZED'
    OR
    (
      rating IS NULL
      AND comment IS NULL
      AND next_time_note IS NULL
    )
  ) NOT VALID;
