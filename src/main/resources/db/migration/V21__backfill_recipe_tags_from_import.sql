-- 원문 분류(RCP_WAY2/RCP_PAT2)를 태그 부여로 옮긴다. recipe_tags 가 0행이라 태그가
-- 사실상 없는 상태를 푸는 첫 걸음이다.
--
-- 임포터가 원문 분류를 버리지 않고 recipes.description 에 문자열로 남겨 뒀다:
--
--   월계수잎과 통후추 등을 사용해 …
--   조리방법: 굽기 | 요리종류: 반찬
--   1인분 310.8kcal · …
--
-- 운영 1,150행 중 1,142행이 이 꼴이다. 나머지 8행은 Flyway 시드 레시피(라면·김치볶음밥 등)로
-- 애초에 식약처 데이터가 아니라 분류가 없다.
--
-- 도구(infra/maintenance/tools/extract_recipe_tags.py)를 쓰지 않고 마이그레이션으로 하는 이유:
--
--   1. 그 도구는 원문 API 를 다시 받아 **제목으로** 맞춘다. 제목은 유일하지 않아
--      겹치는 7종 14행은 축별로 판단 불가가 되어 남는다. 여기서는 각 행이 자기 분류를
--      들고 있어 매칭이라는 단계 자체가 없다.
--   2. 그 도구는 원문 API 키와 운영 DB 덤프를 둘 다 요구한다. 배포로 적용되는 이 방식은
--      둘 다 필요 없다.
--
-- 도구가 쓸모없다는 뜻은 아니다. 원문에만 있고 DB 에 안 남은 값(문화권 등)이나 출처
-- 번호(V18 source_ref) 백필은 여전히 원문 대조가 필요하다.
--
-- assigned_by='IMPORT': 사람이 고른 것도(MANUAL) 규칙이 유도한 것도(RULE) 아니고,
-- 적재된 원문에 있던 값이다.
--
-- '기타'(조리법 307행·음식형태 37행)는 사전에 태그가 없으므로 매칭되지 않고 미부여로 남는다.
-- V14 의 설계 그대로다 — 뜻이 없는 값에 태그를 만들면 눌렀을 때 아무 공통점 없는 목록이 나온다.

INSERT INTO recipe_tags (recipe_id, tag_code, axis_code, assigned_by)
SELECT r.id, m.code, 'METHOD', 'IMPORT'
  FROM recipes r
  JOIN (VALUES
          ('끓이기', 'METHOD_BOIL'),
          ('굽기',   'METHOD_GRILL'),
          ('볶기',   'METHOD_STIR_FRY'),
          ('찌기',   'METHOD_STEAM'),
          ('튀기기', 'METHOD_DEEP_FRY')
       ) AS m(label, code)
    ON m.label = btrim(substring(r.description from '조리방법:[[:space:]]*([^|\n]+)'))
ON CONFLICT (recipe_id, tag_code) DO NOTHING;

-- 원문 라벨과 사전 라벨이 한 군데 다르다: 원문 '국&찌개' vs 사전 '국·찌개'.
-- 라벨을 맞추는 대신 여기서 명시적으로 잇는다 — 사전 라벨은 화면에 나가는 값이라
-- 원문 표기에 끌려다니면 안 된다.
INSERT INTO recipe_tags (recipe_id, tag_code, axis_code, assigned_by)
SELECT r.id, d.code, 'DISH', 'IMPORT'
  FROM recipes r
  JOIN (VALUES
          ('반찬',   'DISH_SIDE'),
          ('일품',   'DISH_MAIN'),
          ('후식',   'DISH_DESSERT'),
          ('밥',     'DISH_RICE'),
          ('국&찌개', 'DISH_SOUP_STEW')
       ) AS d(label, code)
    ON d.label = btrim(substring(r.description from '요리종류:[[:space:]]*([^|\n]+)'))
ON CONFLICT (recipe_id, tag_code) DO NOTHING;
