-- MODIFY amount 의 세 상태를 보존한다.
--   amount_override_present = FALSE          → amount 키 생략, 원본 양 유지
--   amount_override_present = TRUE, amount NULL → 명시적 양 제거
--   amount_override_present = TRUE, amount 값   → 해당 값으로 덮어쓰기
--
-- DEFAULT FALSE 를 유지해 이전 바이너리가 이 컬럼을 모른 채 쓰더라도 INSERT 자체는 깨지지
-- 않는다. 애플리케이션은 legacy/rollback writer 가 남긴 non-null MODIFY 를 값 오버라이드로
-- 읽는 방어선도 둔다.
ALTER TABLE personal_ingredient_adjustments
  ADD COLUMN amount_override_present BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE personal_ingredient_adjustments
SET amount_override_present = TRUE
WHERE adjustment_type = 'MODIFY'
  AND amount IS NOT NULL;

ALTER TABLE personal_ingredient_adjustments
  ADD CONSTRAINT chk_pia_amount_override_present
  CHECK (NOT amount_override_present OR adjustment_type = 'MODIFY');
