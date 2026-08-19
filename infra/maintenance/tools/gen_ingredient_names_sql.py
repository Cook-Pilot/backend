import json, uuid

rb = json.load(open("rebuild.json"))
det = {r["id"]: r for r in json.load(open("details.json"))}

NS = uuid.uuid5(uuid.NAMESPACE_URL, "https://cookpilot.app/recipe-ingredient")

rows = []
for e in rb:
    for n, it in enumerate(e["items"]):
        iid = uuid.uuid5(NS, f"{e['id']}:{n}:{it['name']}")
        rows.append((str(iid), e["id"], it["name"], it["amount"], it["unit"], n))

assert len({r[0] for r in rows}) == len(rows), "생성한 UUID 충돌"

target_ids = [e["id"] for e in rb]
cur_rows = sum(len(det[i]["ingredients"]) for i in target_ids)
new_rows = len(rows)
tot_recipes = len(det)
tot_ing = sum(len(r["ingredients"]) for r in det.values())


def q(s):
    return "'" + s.replace("'", "''") + "'"


def num(v):
    return "NULL" if v is None else f"{v:g}"


lines = []
lines.append(f"""-- CookPilot 운영 레시피 재료 이름 오염 복구 (2026-08-17).
--
-- 증상: 재료 이름에 그룹 머리말과 괄호 안 하위 재료가 섞여 들어가고, 그 과정에서
-- 재료 자체가 소실된 행이 있다. 임포터가 원문(RCP_PARTS_DTLS)을 쉼표로만 쪼갠 결과다.
--   '올리브유 5 양념장: 간장'   = 4     재료 두 개가 한 행에 뭉치고 올리브유 5g 소실
--   '재료 검은깨'              = 8     콜론 없는 그룹 머리말이 이름에 붙음
--   '방울토마토(20g) 소스 통계피' = 20    닫는 괄호 뒤 머리말을 못 끊어 통계피 소실
--   '채소국물 8컵(표고버섯'      = NULL  괄호 안 하위 재료가 쉼표에서 절단
--
-- 운영 스냅샷 기준 오염 770행 / 444개 레시피. 이 스크립트는 그 중 **원문을 다시
-- 파싱해서 확실하게 읽히는 {len(rb)}개 레시피만** 고친다(오염 503행 해소). 남는
-- 267행은 원문 자체가 애매한 것들이라(각 15g씩, 1캔=130g, 원문 괄호 오타) 손대지 않는다.
--
-- 이름만 고칠 수 없어 재료 행을 재구성한다 — 소실된 재료를 되살리려면 INSERT 가
-- 필요하고, 기존 재료 id 생성 규칙(UUIDv5, 네임스페이스 불명)을 재현할 수 없기 때문이다.
-- 그래서 대상 레시피의 재료 행을 지우고 파싱 결과로 다시 넣는다.
--
-- ** 이 방식이 안전한 이유와, 안전하지 않게 되는 조건 **
-- personal_ingredient_adjustments.original_ingredient_id 는 recipe_ingredients(id) 를
-- ON DELETE CASCADE 로 참조한다. 즉 재료 행을 지우면 사용자의 개인 레시피 수정이
-- 조용히 함께 삭제된다. 지금은 그 테이블이 0행이라 잃을 것이 없다.
-- **사용자가 개인화를 시작한 뒤에는 이 스크립트를 그대로 쓰면 안 된다.**
-- 아래 전제조건이 그 조건을 검사해서, 참조가 하나라도 있으면 실행을 거부한다.
--
-- 재구성 행의 id 는 uuid5(uuid5(URL,'https://cookpilot.app/recipe-ingredient'),
-- '<recipe_id>:<sort_order>:<name>') 로 결정론적으로 만들었다. 같은 입력이면 같은 id 다.
--
-- 일회성 유지보수 스크립트다(Flyway 아님). 감사 시점과 데이터가 다르면 일부러 실패한다.

BEGIN;

LOCK TABLE recipes, recipe_ingredients, personal_ingredient_adjustments,
           recommendation_feedback IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE target_recipe (id UUID PRIMARY KEY) ON COMMIT DROP;
CREATE TEMP TABLE new_ingredient (
  id UUID PRIMARY KEY, recipe_id UUID NOT NULL, name TEXT NOT NULL,
  amount NUMERIC(10, 2), unit TEXT, sort_order INT NOT NULL
) ON COMMIT DROP;

INSERT INTO target_recipe (id) VALUES""")

vals = [f"  ('{i}')" for i in target_ids]
lines.append(",\n".join(vals) + "\n;")

lines.append("\nINSERT INTO new_ingredient (id, recipe_id, name, amount, unit, sort_order) VALUES")
cur_title = None
vals = []
for iid, rid, name, amt, unit, so in rows:
    t = det[rid]["title"]
    if t != cur_title:
        vals.append(f"  -- {t}")
        cur_title = t
    u = "NULL" if unit is None else q(unit)
    vals.append(f"  ('{iid}', '{rid}', {q(name)}, {num(amt)}, {u}, {so}),")
vals[-1] = vals[-1].rstrip(",")
lines.append("\n".join(vals) + "\n;")

lines.append(f"""
DO $$
DECLARE
  n INT;
BEGIN
  SELECT count(*) INTO n FROM target_recipe;
  IF n <> {len(rb)} THEN RAISE EXCEPTION '대상 레시피가 {len(rb)}개여야 하는데 %개다', n; END IF;

  SELECT count(*) INTO n FROM new_ingredient;
  IF n <> {new_rows} THEN RAISE EXCEPTION '재구성 행이 {new_rows}개여야 하는데 %개다', n; END IF;

  -- 사용자 개인화 데이터가 재료 행을 참조하기 시작했다면 절대 지우면 안 된다.
  SELECT count(*) INTO n FROM personal_ingredient_adjustments;
  IF n <> 0 THEN
    RAISE EXCEPTION 'personal_ingredient_adjustments 에 %행이 있다. DELETE 가 CASCADE 로 '
                    '사용자 데이터를 지우므로 중단한다 — UPDATE 방식으로 다시 설계할 것', n;
  END IF;

  SELECT count(*) INTO n FROM recommendation_feedback WHERE original_ingredient_id IS NOT NULL;
  IF n <> 0 THEN
    RAISE EXCEPTION 'recommendation_feedback 이 재료 행 %건을 참조한다. 중단한다', n;
  END IF;

  -- 감사 스냅샷 확인.
  SELECT count(*) INTO n FROM recipes;
  IF n <> {tot_recipes} THEN RAISE EXCEPTION '감사 스냅샷은 레시피 {tot_recipes}개인데 %개다', n; END IF;

  SELECT count(*) INTO n FROM recipe_ingredients;
  IF n <> {tot_ing} THEN RAISE EXCEPTION '감사 스냅샷은 재료 {tot_ing}행인데 %행이다', n; END IF;

  SELECT count(*) INTO n FROM target_recipe t
  WHERE NOT EXISTS (SELECT 1 FROM recipes r WHERE r.id = t.id);
  IF n <> 0 THEN RAISE EXCEPTION '대상 레시피 %개가 존재하지 않는다', n; END IF;

  -- 대상 레시피의 현재 재료 행 수가 감사 시점과 같아야 한다.
  SELECT count(*) INTO n FROM recipe_ingredients ri
  WHERE ri.recipe_id IN (SELECT id FROM target_recipe);
  IF n <> {cur_rows} THEN
    RAISE EXCEPTION '대상 레시피의 현재 재료가 {cur_rows}행이어야 하는데 %행이다', n;
  END IF;

  -- 생성한 id 가 대상 밖의 기존 행과 충돌하면 안 된다.
  SELECT count(*) INTO n FROM new_ingredient ni
  JOIN recipe_ingredients ri ON ri.id = ni.id
  WHERE ri.recipe_id NOT IN (SELECT id FROM target_recipe);
  IF n <> 0 THEN RAISE EXCEPTION '생성한 id 가 대상 밖 기존 행 %건과 충돌한다', n; END IF;
END $$;

DELETE FROM recipe_ingredients WHERE recipe_id IN (SELECT id FROM target_recipe);

INSERT INTO recipe_ingredients (id, recipe_id, name, amount, unit, is_required, sort_order)
SELECT id, recipe_id, name, amount, unit, TRUE, sort_order FROM new_ingredient;

DO $$
DECLARE
  n INT;
BEGIN
  SELECT count(*) INTO n FROM recipe_ingredients;
  IF n <> {tot_ing - cur_rows + new_rows} THEN
    RAISE EXCEPTION '재구성 후 재료가 {tot_ing - cur_rows + new_rows}행이어야 하는데 %행이다', n;
  END IF;

  -- 재구성한 행에 오염이 남아 있으면 안 된다.
  SELECT count(*) INTO n FROM recipe_ingredients ri
  WHERE ri.recipe_id IN (SELECT id FROM target_recipe)
    AND (ri.name LIKE '%:%' OR ri.name LIKE '%[%' OR ri.name LIKE '%]%'
         OR ri.name LIKE '%<br>%'
         OR (length(ri.name) - length(replace(ri.name, '(', ''))) <>
             (length(ri.name) - length(replace(ri.name, ')', ''))));
  IF n <> 0 THEN RAISE EXCEPTION '재구성 후에도 이름이 오염된 행이 %개 있다', n; END IF;

  -- 수량 없는 행이 생기면 안 된다(파서가 확신한 레시피만 대상이므로).
  SELECT count(*) INTO n FROM recipe_ingredients ri
  WHERE ri.recipe_id IN (SELECT id FROM target_recipe)
    AND ri.amount IS NULL AND ri.unit IS NULL;
  IF n <> 0 THEN RAISE EXCEPTION '재구성 후 수량이 전혀 없는 행이 %개 있다', n; END IF;
END $$;

COMMIT;
""")

open("names-apply.sql", "w").write("\n".join(lines))
print(f"names-apply.sql 생성: 레시피 {len(rb)}개, 현재 {cur_rows}행 → 재구성 {new_rows}행")
print(f"전체 재료: {tot_ing} → {tot_ing - cur_rows + new_rows}")
