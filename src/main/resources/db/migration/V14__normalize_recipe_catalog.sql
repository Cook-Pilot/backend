-- 운영 카탈로그의 명확한 중복·수량·대표 이미지만 보수적으로 정리한다.
-- 분수/범위/이중표현/괄호 손상 데이터는 원문 손실을 피하려고 의도적으로 제외한다.

-- 이름 끝의 단일 숫자 수량을 구조화한다. 예: 가지(90g) -> 가지 / 90 / g
WITH parsed AS (
  SELECT
    ri.id,
    trim(match[1]) AS new_name,
    replace(match[2], ',', '.')::numeric AS new_amount,
    CASE lower(match[3])
      WHEN 'ml' THEN 'ml'
      ELSE match[3]
    END AS new_unit
  FROM recipe_ingredients ri
  CROSS JOIN LATERAL regexp_matches(
    ri.name,
    E'^(.*[^[:space:]])[[:space:]]*\\([[:space:]]*([0-9]+(?:\\.[0-9]+|,[0-9]{1,2})?)[[:space:]]*(kg|g|mg|ml|cc|컵|큰술|작은술|스푼|숟가락|티스푼|개|마리|장|쪽|톨|포기|단|대|줌|꼬집|봉|캔|팩|병|알|줄기|송이|조각|인분|공기)[[:space:]]*\\)[[:space:]]*$',
    'i'
  ) AS captured(match)
  WHERE ri.amount IS NULL
    AND ri.unit IS NULL
)
UPDATE recipe_ingredients ri
SET name = parsed.new_name,
    amount = parsed.new_amount,
    unit = parsed.new_unit
FROM parsed
WHERE ri.id = parsed.id
  AND ri.amount IS NULL
  AND ri.unit IS NULL;

-- 원본에서 Ts/ts는 각각 큰술/작은술 의미로 사용됐다. 대소문자를 섞지 않는다.
WITH parsed AS (
  SELECT
    ri.id,
    trim(match[1]) AS new_name,
    replace(match[2], ',', '.')::numeric AS new_amount
  FROM recipe_ingredients ri
  CROSS JOIN LATERAL regexp_matches(
    ri.name,
    E'^(.*[^[:space:]])[[:space:]]*\\([[:space:]]*([0-9]+(?:\\.[0-9]+|,[0-9]{1,2})?)[[:space:]]*Ts[[:space:]]*\\)[[:space:]]*$'
  ) AS captured(match)
  WHERE ri.amount IS NULL
    AND ri.unit IS NULL
)
UPDATE recipe_ingredients ri
SET name = parsed.new_name,
    amount = parsed.new_amount,
    unit = '큰술'
FROM parsed
WHERE ri.id = parsed.id
  AND ri.amount IS NULL
  AND ri.unit IS NULL;

WITH parsed AS (
  SELECT
    ri.id,
    trim(match[1]) AS new_name,
    replace(match[2], ',', '.')::numeric AS new_amount
  FROM recipe_ingredients ri
  CROSS JOIN LATERAL regexp_matches(
    ri.name,
    E'^(.*[^[:space:]])[[:space:]]*\\([[:space:]]*([0-9]+(?:\\.[0-9]+|,[0-9]{1,2})?)[[:space:]]*ts[[:space:]]*\\)[[:space:]]*$'
  ) AS captured(match)
  WHERE ri.amount IS NULL
    AND ri.unit IS NULL
)
UPDATE recipe_ingredients ri
SET name = parsed.new_name,
    amount = parsed.new_amount,
    unit = '작은술'
FROM parsed
WHERE ri.id = parsed.id
  AND ri.amount IS NULL
  AND ri.unit IS NULL;

-- 유니코드 밀리리터 기호는 API 표준 표기인 ml로 정규화한다.
WITH parsed AS (
  SELECT
    ri.id,
    trim(match[1]) AS new_name,
    replace(match[2], ',', '.')::numeric AS new_amount
  FROM recipe_ingredients ri
  CROSS JOIN LATERAL regexp_matches(
    ri.name,
    E'^(.*[^[:space:]])[[:space:]]*\\([[:space:]]*([0-9]+(?:\\.[0-9]+|,[0-9]{1,2})?)[[:space:]]*㎖[[:space:]]*\\)[[:space:]]*$'
  ) AS captured(match)
  WHERE ri.amount IS NULL
    AND ri.unit IS NULL
)
UPDATE recipe_ingredients ri
SET name = parsed.new_name,
    amount = parsed.new_amount,
    unit = 'ml'
FROM parsed
WHERE ri.id = parsed.id
  AND ri.amount IS NULL
  AND ri.unit IS NULL;

-- 정확히 '(약간)'으로 끝나는 경우만 정성 수량으로 구조화한다.
WITH parsed AS (
  SELECT
    ri.id,
    trim(match[1]) AS new_name
  FROM recipe_ingredients ri
  CROSS JOIN LATERAL regexp_matches(
    ri.name,
    E'^(.*[^[:space:]])[[:space:]]*\\([[:space:]]*약간[[:space:]]*\\)[[:space:]]*$'
  ) AS captured(match)
  WHERE ri.amount IS NULL
    AND ri.unit IS NULL
)
UPDATE recipe_ingredients ri
SET name = parsed.new_name,
    unit = '약간'
FROM parsed
WHERE ri.id = parsed.id
  AND ri.amount IS NULL
  AND ri.unit IS NULL;

-- 조리법과 재료가 실질적으로 같은 고신뢰 중복본만 목록에서 숨긴다.
-- 삭제하지 않아 외부 링크와 향후 사용자 참조를 보존한다.
UPDATE recipes
SET status = 'inactive',
    updated_at = NOW()
WHERE status = 'active'
  AND id IN (
    '1fa07d9c-c55a-556a-be1b-1c7539bc003e', -- 가지볶음
    '2a835a97-7580-5aa0-8509-6f4326041a7d', -- 구운 주먹밥
    '09675db0-2383-55ad-8e36-765578d8dcef', -- 버섯 리조또
    'a5fc366f-f6f7-5f38-ac34-f5e87841c840', -- 봄주먹밥
    '5248e303-9c41-5767-bb8e-84438669ddb9', -- 비타 오이 물김치
    '5b2dac01-f627-540a-afa4-d5f9d7f449df', -- 자색고구마 호떡
    '27d91d50-cd38-521f-affa-bc6b60da14cb'  -- 효종갱(曉鍾羹)
  );

-- 재료 배치 사진이 대표로 연결된 원본군은 접근 가능한 마지막 완성 단계 사진으로 교체한다.
UPDATE recipes r
SET image_url = (
      SELECT s.image_url
      FROM recipe_steps s
      WHERE s.recipe_id = r.id
        AND s.image_url IS NOT NULL
      ORDER BY s.step_index DESC
      LIMIT 1
    ),
    updated_at = NOW()
WHERE r.status = 'active'
  AND r.image_url LIKE '%/common/ecmFileView.do%'
  AND EXISTS (
    SELECT 1
    FROM recipe_steps s
    WHERE s.recipe_id = r.id
      AND s.image_url IS NOT NULL
  );

-- 홈페이지 HTML을 이미지처럼 가리키던 한 건도 마지막 완성 단계 사진으로 복구한다.
UPDATE recipes r
SET image_url = (
      SELECT s.image_url
      FROM recipe_steps s
      WHERE s.recipe_id = r.id
        AND s.image_url IS NOT NULL
      ORDER BY s.step_index DESC
      LIMIT 1
    ),
    updated_at = NOW()
WHERE r.id = '20d8e75e-eaaf-5c04-98f3-16ebdce11b33'
  AND r.image_url IN (
    'http://www.foodsafetykorea.go.kr/',
    'https://www.foodsafetykorea.go.kr/'
  );

-- 원본 COOKRCP01 저해상도 이미지를 충실하게 고해상도화한 대표 이미지로 교체한다.
-- 객체는 공개 경로에 먼저 업로드한 뒤 이 마이그레이션을 적용한다.
UPDATE recipes
SET image_url = CASE id
      WHEN 'a32d03c6-9e44-559f-a7eb-76fd96f74887' THEN 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/catalog-recipes/eggplant-tangsuyuk.png'
      WHEN '5387fc32-129b-502f-8aac-967691e4aa15' THEN 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/catalog-recipes/perilla-pickle-roll.png'
      WHEN '7c107900-d189-547d-acea-10b601ebdf54' THEN 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/catalog-recipes/tofu-apple-cucumber.png'
      WHEN 'd88c3b38-5758-568f-b375-db3a5d345498' THEN 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/catalog-recipes/chamnamul-radish-water-kimchi.png'
    END,
    updated_at = NOW()
WHERE status = 'active'
  AND id IN (
    'a32d03c6-9e44-559f-a7eb-76fd96f74887',
    '5387fc32-129b-502f-8aac-967691e4aa15',
    '7c107900-d189-547d-acea-10b601ebdf54',
    'd88c3b38-5758-568f-b375-db3a5d345498'
  );
