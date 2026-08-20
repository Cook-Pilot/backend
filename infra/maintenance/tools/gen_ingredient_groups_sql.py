import json, collections

assign = json.load(open("group_assign.json"))
det = json.load(open("details.json"))
live = {i["id"]: (r["title"], i["name"]) for r in det for i in r["ingredients"]}

assert all(k in live for k in assign), "운영에 없는 id 가 있다"

tot_ing = sum(len(r["ingredients"]) for r in det)
tot_recipes = len(det)
n_groups = len(set(assign.values()))

# 레시피 제목 순으로 정렬해 사람이 읽기 쉽게
rows = sorted(assign.items(), key=lambda kv: (live[kv[0]][0], live[kv[0]][1]))


def q(s):
    return "'" + s.replace("'", "''") + "'"


vals = []
cur_title = None
for iid, g in rows:
    t = live[iid][0]
    if t != cur_title:
        vals.append(f"  -- {t}")
        cur_title = t
    vals.append(f"  ('{iid}', {q(g)}),   -- {live[iid][1]}")
vals[-1] = vals[-1].replace("),   --", ")    --", 1)

dist = collections.Counter(assign.values()).most_common()
dist_lines = "\n".join(f"--   {c:5d}  {g}" for g, c in dist[:20])

sql = f"""-- CookPilot 운영 레시피 재료 그룹 백필 (2026-08-17).
--
-- 재료를 묶는 그룹 이름(주재료/양념장/소스/국물…)을 원문에서 다시 뽑아 채운다.
-- V15__add_recipe_ingredient_group.sql 이 만드는 ingredient_group 컬럼을 채우는 작업이다.
--
-- 이 정보는 원래 원문 RCP_PARTS_DTLS 에 있었는데, 담을 컬럼이 없어서
-- 재료 이름 오염 복구(2026-08-17-recipe-ingredient-names.sql) 때 버렸다.
-- 나중에 재료를 분류할 때 필요하다는 판단으로 되살린다.
--
-- 매칭 경로는 두 개뿐이고, 둘 다 아니면 그룹을 비워 둔다(추측하지 않는다).
--   A) 이름 복구로 재구성한 305개 레시피 — id 가 결정론적이라(uuid5) 다시 계산된다.
--   B) 그 밖의 레시피 — 원문 파싱 결과가 현재 행과 (이름, 수량, 단위) 순서까지
--      완전히 일치하는 경우만. 하나라도 어긋나면 그 레시피 전체를 건너뛴다.
-- 결과: 952개 레시피 / {len(assign)}개 재료 행에 그룹이 붙는다.
-- 건너뛴 것 — 원문 없음 13(수동 시드), 원문이 애매함 113, 정렬 불일치 72.
--
-- 그룹 값은 원문 머리말 그대로다({n_groups}종). 상위 20:
{dist_lines}
-- '주재료'/'재료', '필수 재료'/'필수재료' 처럼 같은 뜻의 다른 표기가 섞여 있다.
-- 임의로 합치지 않았다 — 분류 체계를 세울 때 정할 몫이고, 원문을 보존하는 편이
-- 나중에 되돌릴 수 있다.
--
-- 일회성 유지보수 스크립트다. 감사 시점과 데이터가 다르면 일부러 실패한다.

BEGIN;

LOCK TABLE recipe_ingredients IN SHARE ROW EXCLUSIVE MODE;

-- 운영에는 배포 전에 컬럼을 먼저 넣는다. 배포 때 V15 가 IF NOT EXISTS 로 지나간다.
ALTER TABLE recipe_ingredients ADD COLUMN IF NOT EXISTS ingredient_group TEXT;
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_group
  ON recipe_ingredients(recipe_id, ingredient_group);

CREATE TEMP TABLE group_backfill (
  id    UUID PRIMARY KEY,
  grp   TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO group_backfill (id, grp) VALUES
{chr(10).join(vals)}
;

DO $$
DECLARE
  n INT;
BEGIN
  SELECT count(*) INTO n FROM group_backfill;
  IF n <> {len(assign)} THEN RAISE EXCEPTION '백필 목록이 {len(assign)}행이어야 하는데 %행이다', n; END IF;

  -- 감사 스냅샷 확인(이름 복구 반영 후 상태).
  SELECT count(*) INTO n FROM recipes;
  IF n <> {tot_recipes} THEN RAISE EXCEPTION '감사 스냅샷은 레시피 {tot_recipes}개인데 %개다', n; END IF;

  SELECT count(*) INTO n FROM recipe_ingredients;
  IF n <> {tot_ing} THEN RAISE EXCEPTION '감사 스냅샷은 재료 {tot_ing}행인데 %행이다', n; END IF;

  SELECT count(*) INTO n FROM group_backfill b
  WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients ri WHERE ri.id = b.id);
  IF n <> 0 THEN RAISE EXCEPTION '백필 대상 %행이 recipe_ingredients 에 없다', n; END IF;

  -- 이미 채워진 그룹이 있으면 덮어쓰지 않고 멈춘다.
  SELECT count(*) INTO n FROM recipe_ingredients WHERE ingredient_group IS NOT NULL;
  IF n <> 0 THEN
    RAISE EXCEPTION 'ingredient_group 이 이미 %행 채워져 있다. 스크립트를 다시 검토할 것', n;
  END IF;
END $$;

UPDATE recipe_ingredients ri
SET ingredient_group = b.grp
FROM group_backfill b
WHERE ri.id = b.id;

DO $$
DECLARE
  n INT;
BEGIN
  SELECT count(*) INTO n FROM recipe_ingredients WHERE ingredient_group IS NOT NULL;
  IF n <> {len(assign)} THEN
    RAISE EXCEPTION '백필 후 그룹이 {len(assign)}행이어야 하는데 %행이다', n;
  END IF;

  SELECT count(DISTINCT ingredient_group) INTO n FROM recipe_ingredients
  WHERE ingredient_group IS NOT NULL;
  IF n <> {n_groups} THEN RAISE EXCEPTION '그룹 값이 {n_groups}종이어야 하는데 %종이다', n; END IF;

  -- 그룹 값에 오염(콜론·대괄호·숫자만)이 섞이면 안 된다.
  SELECT count(*) INTO n FROM recipe_ingredients
  WHERE ingredient_group ~ '[:\\[\\]]' OR ingredient_group ~ '^[0-9.]+$';
  IF n <> 0 THEN RAISE EXCEPTION '그룹 값이 오염된 행이 %개 있다', n; END IF;
END $$;

COMMIT;

-- 적용 후 확인용(별도 실행):
--   SELECT ri.ingredient_group, ri.name, ri.amount, ri.unit
--   FROM recipe_ingredients ri JOIN recipes r ON r.id = ri.recipe_id
--   WHERE r.title = '감자냉채' ORDER BY ri.sort_order;
"""

open("groups-apply.sql", "w").write(sql)
print(f"groups-apply.sql 생성: {len(assign)}행, 그룹 {n_groups}종")
