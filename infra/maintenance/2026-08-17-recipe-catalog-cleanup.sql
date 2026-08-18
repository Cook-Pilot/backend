-- CookPilot production recipe catalog cleanup (2026-08-17).
--
-- Preconditions were audited against the production snapshot backed up at:
-- s3://cookpilot-backup-167403240280/manual/
--   cookpilot-pre-recipe-cleanup-20260817T044136Z.sql.gz
--
-- This is a one-time maintenance script, not a Flyway migration. It deliberately
-- fails if the audited production snapshot has changed, so a future operator
-- cannot replay the destructive duplicate deletion against different data.

BEGIN;

LOCK TABLE
  recipes,
  recipe_ingredients,
  recipe_steps,
  recipe_favorites,
  post_cook_reviews,
  personal_recipe_versions,
  recipe_flavor_profiles,
  recommendation_feedback,
  personal_ingredient_adjustments,
  personal_step_adjustments
IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE recipe_cleanup_duplicate_map (
  delete_id UUID PRIMARY KEY,
  keep_id   UUID NOT NULL
) ON COMMIT DROP;

INSERT INTO recipe_cleanup_duplicate_map (delete_id, keep_id) VALUES
  ('7d593cae-a786-50ca-8958-7fb718572d30', '1fa07d9c-c55a-556a-be1b-1c7539bc003e'),
  ('2ec3b758-d3fa-5acd-b569-b40f2ed1a854', 'aa2b7d59-9137-5307-b45c-75d1e0ed7b5a'),
  ('654fa527-6c71-56d1-a757-665586c80b1b', '2a835a97-7580-5aa0-8509-6f4326041a7d'),
  ('5387fc32-129b-502f-8aac-967691e4aa15', 'a662b510-c15c-568d-821c-f5bd7c59fdce'),
  ('7f6f54c6-3edd-5249-9db8-8655fbfa81d7', '107cdce1-b7ea-5a97-9147-fea425e8f601'),
  ('d1991f69-fce1-51e4-9e8d-2122d8364bce', 'e8f6b208-ffe5-5a82-ab2a-c21642f78742'),
  ('e17eb166-821e-53d0-ae67-d75abb929aac', '9980078f-3fc0-52d9-9de8-4db31dd25da4'),
  ('09675db0-2383-55ad-8e36-765578d8dcef', '2914eb3b-ce45-5847-891e-e1976907a913'),
  ('55b54bc7-d916-5394-b4a7-6c78368b05a3', '2914eb3b-ce45-5847-891e-e1976907a913'),
  ('a5fc366f-f6f7-5f38-ac34-f5e87841c840', 'b3de4775-691a-51ca-85f4-4ef84f53541a'),
  ('5248e303-9c41-5767-bb8e-84438669ddb9', 'f7d43f57-53a6-575b-8d72-24c756da40f0'),
  ('80d8762b-2b5b-517d-ae25-75c490f642cf', 'a37ffdaa-ff6c-5d45-bb3d-9868d324f8d0'),
  ('1c8eae8d-0984-506a-b652-7b7b3183fec9', '0238e219-2c3f-5118-96d4-190880d5c425'),
  ('b913ebd8-29bd-5d35-b80f-0917b97b3f31', '5b2dac01-f627-540a-afa4-d5f9d7f449df');

DO $$
DECLARE
  recipe_count INT;
  missing_targets INT;
  user_reference_count INT;
BEGIN
  SELECT count(*) INTO recipe_count FROM recipes;
  IF recipe_count <> 1164 THEN
    RAISE EXCEPTION 'expected 1164 recipes from audited snapshot, found %', recipe_count;
  END IF;

  SELECT count(*) INTO missing_targets
  FROM recipe_cleanup_duplicate_map m
  WHERE NOT EXISTS (SELECT 1 FROM recipes r WHERE r.id = m.delete_id)
     OR NOT EXISTS (SELECT 1 FROM recipes r WHERE r.id = m.keep_id);
  IF missing_targets <> 0 THEN
    RAISE EXCEPTION '% duplicate mapping targets are missing', missing_targets;
  END IF;

  SELECT
      (SELECT count(*) FROM recipe_favorites f
       JOIN recipe_cleanup_duplicate_map m ON m.delete_id = f.recipe_id)
    + (SELECT count(*) FROM post_cook_reviews r
       JOIN recipe_cleanup_duplicate_map m ON m.delete_id = r.recipe_id)
    + (SELECT count(*) FROM personal_recipe_versions v
       JOIN recipe_cleanup_duplicate_map m ON m.delete_id = v.recipe_id)
    + (SELECT count(*) FROM recommendation_feedback f
       JOIN recipe_cleanup_duplicate_map m ON m.delete_id = f.recipe_id)
  INTO user_reference_count;
  IF user_reference_count <> 0 THEN
    RAISE EXCEPTION 'duplicate recipes gained % user references after audit', user_reference_count;
  END IF;
END $$;

-- Every delete target has zero user references. Child ingredients, steps, and
-- optional flavor profiles are catalog-owned and are removed by their FKs.
DELETE FROM recipes r
USING recipe_cleanup_duplicate_map m
WHERE r.id = m.delete_id;

CREATE TEMP TABLE recipe_cleanup_titles (
  recipe_id UUID PRIMARY KEY,
  title     TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO recipe_cleanup_titles (recipe_id, title) VALUES
  ('1fa07d9c-c55a-556a-be1b-1c7539bc003e', '가지볶음'),
  ('aa2b7d59-9137-5307-b45c-75d1e0ed7b5a', '곤약 백김치 말이'),
  ('2a835a97-7580-5aa0-8509-6f4326041a7d', '구운 주먹밥'),
  ('a662b510-c15c-568d-821c-f5bd7c59fdce', '깻잎장아찌 롤'),
  ('107cdce1-b7ea-5a97-9147-fea425e8f601', '둥지 튀김'),
  ('e8f6b208-ffe5-5a82-ab2a-c21642f78742', '바지락 맑은국'),
  ('9980078f-3fc0-52d9-9de8-4db31dd25da4', '배 깍두기'),
  ('2914eb3b-ce45-5847-891e-e1976907a913', '버섯 리조또'),
  ('b3de4775-691a-51ca-85f4-4ef84f53541a', '봄 주먹밥'),
  ('f7d43f57-53a6-575b-8d72-24c756da40f0', '비타 오이 물김치'),
  ('a37ffdaa-ff6c-5d45-bb3d-9868d324f8d0', '새우 완자탕'),
  ('0238e219-2c3f-5118-96d4-190880d5c425', '양배추 롤'),
  ('5b2dac01-f627-540a-afa4-d5f9d7f449df', '자색고구마 호떡'),
  ('856651e1-552c-57bc-ad7b-af8c901674e7', 'LA 갈비구이');

UPDATE recipes r
SET title = t.title,
    updated_at = NOW()
FROM recipe_cleanup_titles t
WHERE r.id = t.recipe_id
  AND r.title IS DISTINCT FROM t.title;

-- Split a terminal quantity such as "양파(20g)" into the existing structured
-- fields. Descriptive parentheses (for example, "소고기(불고기용)") do not
-- match these rules and remain part of the ingredient name.
CREATE TEMP TABLE recipe_cleanup_ingredient_updates
ON COMMIT DROP AS
WITH extracted AS (
  SELECT
    id,
    btrim(regexp_replace(name, '[[:space:]]*\([^()]*\)[[:space:]]*$', '')) AS base_name,
    coalesce(btrim(substring(name FROM '\(([^()]*)\)[[:space:]]*$')), '') AS suffix
  FROM recipe_ingredients
  WHERE name ~ '\)[[:space:]]*$'
    AND amount IS NULL
    AND unit IS NULL
), parsed AS (
  SELECT
    id,
    CASE
      WHEN suffix = '중간 크기 1개' THEN base_name || ' (중간 크기)'
      WHEN suffix = '노란색 2개' THEN base_name || ' (노란색)'
      ELSE base_name
    END AS new_name,
    CASE
      WHEN suffix ~ '^[0-9]+/[0-9]+[[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
        THEN round(
          substring(suffix FROM '^([0-9]+)')::numeric
          / substring(suffix FROM '^[0-9]+/([0-9]+)')::numeric,
          2
        )
      WHEN suffix ~ '^1½[[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$' THEN 1.5
      WHEN suffix ~ '^[¼½¾⅓⅔⅛⅜⅝⅞][[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
        THEN CASE left(suffix, 1)
          WHEN '¼' THEN 0.25 WHEN '½' THEN 0.5 WHEN '¾' THEN 0.75
          WHEN '⅓' THEN 0.33 WHEN '⅔' THEN 0.67
          WHEN '⅛' THEN 0.13 WHEN '⅜' THEN 0.38
          WHEN '⅝' THEN 0.63 WHEN '⅞' THEN 0.88
        END
      WHEN suffix ~ '^[0-9]+([.][0-9]+)?[[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
        THEN substring(suffix FROM '^([0-9]+([.][0-9]+)?)')::numeric
      WHEN suffix ~ '^[0-9]+([.][0-9]+)?$'
        THEN suffix::numeric
      WHEN suffix = '중간 크기 1개' THEN 1
      WHEN suffix = '노란색 2개' THEN 2
      WHEN suffix = '각 10g' THEN 10
      WHEN suffix = '각 15g씩' THEN 15
      WHEN suffix = '각 2g' THEN 2
      WHEN suffix = '개' THEN 1
      ELSE NULL
    END AS new_amount,
    CASE
      WHEN suffix ~ '^[0-9]+/[0-9]+[[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
        THEN regexp_replace(suffix, '^[0-9]+/[0-9]+[[:space:]]*', '')
      WHEN suffix ~ '^1½[[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
        THEN regexp_replace(suffix, '^1½[[:space:]]*', '')
      WHEN suffix ~ '^[¼½¾⅓⅔⅛⅜⅝⅞][[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
        THEN regexp_replace(suffix, '^[¼½¾⅓⅔⅛⅜⅝⅞][[:space:]]*', '')
      WHEN suffix ~ '^[0-9]+([.][0-9]+)?[[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
        THEN regexp_replace(suffix, '^[0-9]+([.][0-9]+)?[[:space:]]*', '')
      WHEN suffix = '중간 크기 1개' OR suffix = '노란색 2개' OR suffix = '개' THEN '개'
      WHEN suffix = '각 10g' OR suffix = '각 15g씩' OR suffix = '각 2g' THEN 'g씩'
      ELSE NULL
    END AS raw_unit
  FROM extracted
  WHERE suffix ~ '^[0-9]+/[0-9]+[[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
     OR suffix ~ '^1½[[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
     OR suffix ~ '^[¼½¾⅓⅔⅛⅜⅝⅞][[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
     OR suffix ~ '^[0-9]+([.][0-9]+)?[[:space:]]*[[:alpha:]가-힣㎖㎏㎎ℓ%].*$'
     OR suffix ~ '^[0-9]+([.][0-9]+)?$'
     OR suffix = ''
     OR suffix IN (
       '약간', '적당량', '조금', '소량',
       '중간 크기 1개', '노란색 2개',
       '각 10g', '각 15g씩', '각 2g', '개'
     )
)
SELECT
  id,
  new_name,
  new_amount,
  CASE btrim(raw_unit)
    WHEN '㎖' THEN 'ml'
    WHEN '㎎' THEN 'mg'
    WHEN '㎏' THEN 'kg'
    WHEN 'ℓ' THEN 'L'
    WHEN 'Ts' THEN '큰술'
    WHEN 'TS' THEN '큰술'
    WHEN 'T' THEN '큰술'
    WHEN 'tbsp' THEN '큰술'
    WHEN 'ts' THEN '작은술'
    WHEN 't' THEN '작은술'
    WHEN 'tsp' THEN '작은술'
    ELSE regexp_replace(btrim(raw_unit), '[[:space:]]+', '', 'g')
  END AS new_unit
FROM parsed;

DO $$
DECLARE
  invalid_rows INT;
BEGIN
  SELECT count(*) INTO invalid_rows
  FROM recipe_cleanup_ingredient_updates
  WHERE new_name = ''
     OR new_amount < 0;
  IF invalid_rows <> 0 THEN
    RAISE EXCEPTION '% parsed ingredient updates are invalid', invalid_rows;
  END IF;
END $$;

UPDATE recipe_ingredients i
SET name = u.new_name,
    amount = u.new_amount,
    unit = u.new_unit
FROM recipe_cleanup_ingredient_updates u
WHERE i.id = u.id;

UPDATE recipe_ingredients
SET name = 'LA갈비'
WHERE recipe_id = '856651e1-552c-57bc-ad7b-af8c901674e7'
  AND name = 'L.A갈비';

DO $$
DECLARE
  la_step_rows INT;
BEGIN
  UPDATE recipe_steps
  SET instruction = replace(instruction, 'L.A 갈비', 'LA갈비')
  WHERE recipe_id = '856651e1-552c-57bc-ad7b-af8c901674e7'
    AND instruction LIKE '%L.A 갈비%';

  GET DIAGNOSTICS la_step_rows = ROW_COUNT;
  IF la_step_rows <> 2 THEN
    RAISE EXCEPTION 'expected 2 LA갈비 step rows, found %', la_step_rows;
  END IF;
END $$;

DO $$
DECLARE
  remaining_recipes INT;
  duplicate_groups INT;
  suspicious_titles INT;
  simple_quantity_names INT;
BEGIN
  SELECT count(*) INTO remaining_recipes FROM recipes;
  IF remaining_recipes <> 1150 THEN
    RAISE EXCEPTION 'expected 1150 recipes after cleanup, found %', remaining_recipes;
  END IF;

  SELECT count(*) INTO duplicate_groups
  FROM (
    SELECT lower(regexp_replace(btrim(title), '[[:space:]]+', '', 'g'))
    FROM recipes
    GROUP BY 1
    HAVING count(*) > 1
  ) d;
  IF duplicate_groups <> 0 THEN
    RAISE EXCEPTION '% normalized title duplicate groups remain', duplicate_groups;
  END IF;

  SELECT count(*) INTO suspicious_titles
  FROM recipes
  WHERE title <> btrim(title)
     OR title ~ '[[:space:]]{2,}'
     OR title ~ '[.]{2,}'
     OR title ~ '[,!?]{2,}';
  IF suspicious_titles <> 0 THEN
    RAISE EXCEPTION '% suspicious title formatting rows remain', suspicious_titles;
  END IF;

  SELECT count(*) INTO simple_quantity_names
  FROM recipe_ingredients
  WHERE name ~* '\([[:space:]]*[0-9]+([.][0-9]+)?[[:space:]]*(mg|g|kg|ml|l|개|봉|장|쪽|대|모|공기|컵|큰술|작은술|스푼|꼬집|줌|t)[[:space:]]*\)[[:space:]]*$';
  IF simple_quantity_names <> 0 THEN
    RAISE EXCEPTION '% simple parenthesized quantity names remain', simple_quantity_names;
  END IF;
END $$;

SELECT
  (SELECT count(*) FROM recipes) AS recipes_after,
  (SELECT count(*) FROM recipe_cleanup_duplicate_map) AS duplicate_recipes_deleted,
  (SELECT count(*) FROM recipe_cleanup_ingredient_updates) AS ingredient_rows_normalized,
  (SELECT count(*) FROM recipe_ingredients
   WHERE name ~ '\([^()]*[0-9][^()]*\)[[:space:]]*$') AS complex_quantity_names_left_for_schema_support;

COMMIT;
