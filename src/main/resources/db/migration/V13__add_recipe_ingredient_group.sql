-- 재료를 묶는 그룹 이름을 담는다. 원문(식품안전나라 COOKRCP01 의 RCP_PARTS_DTLS)에는
-- '주재료', '양념장', '- 국물 :', '[소스소개] 잣소스' 처럼 재료를 묶는 머리말이 있는데
-- 담을 곳이 없어서 그동안 버려졌다(임포터는 이 머리말을 재료 '이름' 에 섞어 넣었다).
--
-- NULL = 그룹 없음. 원문에 머리말이 없는 재료가 대부분이므로 NULL 이 정상 상태다.
-- 값은 원문 머리말을 그대로 넣는다. '주재료'/'재료', '필수 재료'/'필수재료' 처럼
-- 같은 뜻의 다른 표기가 섞여 있는데, 분류 체계를 세울 때 정규화할 몫으로 남긴다.
--
-- 애플리케이션은 아직 이 컬럼을 매핑하지 않는다(ddl-auto: validate 는 매핑되지 않은
-- 컬럼을 문제 삼지 않는다). 조리 화면에서 그룹별로 묶어 보여주려면 엔티티·DTO·프론트
-- 작업이 따라야 한다.
--
-- IF NOT EXISTS: 운영 DB 에는 이 컬럼을 배포 전에 유지보수 스크립트로 먼저 넣었다
-- (그룹 값 백필이 배포를 기다릴 수 없었다). 그래서 배포 시 이 마이그레이션은 운영에서
-- 아무 일도 하지 않고 지나가고, 새로 만드는 환경에서는 컬럼을 만든다.
ALTER TABLE recipe_ingredients
  ADD COLUMN IF NOT EXISTS ingredient_group TEXT;

-- 그룹별 조회(조리 화면에서 그룹 단위로 묶어 읽기)를 위한 인덱스.
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_group
  ON recipe_ingredients(recipe_id, ingredient_group);
