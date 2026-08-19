"""tag_assign.json 을 운영 반영 SQL 로 만든다.

8/17 데이터 복구와 같은 형태다 — Flyway 마이그레이션이 아니라 일회성 유지보수 스크립트이고,
감사 시점과 데이터가 다르면 **일부러 실패한다**. 태그 부여는 스키마가 아니라 운영 데이터라
새 환경에서 재실행될 대상이 아니다(새 환경은 레시피가 시드 8건뿐이다).

    python3 gen_recipe_tags_sql.py > ../2026-08-19-recipe-tags-backfill.sql
"""
import json
import sys
from collections import Counter

ASSIGNED_BY = "IMPORT"   # 원문에서 그대로 옮긴 것. 사람 판단도 LLM 도 아니다.


def main():
    assignments = json.load(open("tag_assign.json"))
    details = json.load(open("details.json"))

    rows = [(a["recipe_id"], t["code"], t["axis"]) for a in assignments for t in a["tags"]]
    per_code = Counter(code for _, code, _ in rows)
    codes = sorted(per_code)

    out = sys.stdout.write
    out(f"""-- CookPilot 레시피 태그 원문 백필 (생성물 — gen_recipe_tags_sql.py)
--
-- 식품안전나라 COOKRCP01 의 RCP_PAT2(음식형태) / RCP_WAY2(조리법) 를 그대로 옮긴다.
-- 추론이 없다. 원문에 값이 있는 것만, 제목이 정확히 일치할 때만 부여한다.
-- '기타' 와 제목이 겹쳐 값이 갈리는 축은 미부여로 남긴다(추측하지 않는다).
--
-- 부여 {len(rows)}행 / {len(assignments)}레시피. assigned_by = '{ASSIGNED_BY}'.
-- confidence 는 비운다 — 원문 값이지 확신도를 매길 추정이 아니다.
--
-- 일회성이다. 감사 스냅샷과 다르면 실패한다.

BEGIN;

LOCK TABLE recipes, tags, recipe_tags IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE new_tag (recipe_id UUID, tag_code TEXT, axis_code TEXT) ON COMMIT DROP;

INSERT INTO new_tag (recipe_id, tag_code, axis_code) VALUES
""")
    out(",\n".join(f"  ('{rid}', '{code}', '{axis}')" for rid, code, axis in rows) + ";\n")

    out(f"""
-- ── 전제조건 ─────────────────────────────────────────────────────────────────
DO $backfill$
DECLARE n BIGINT;
BEGIN
  SELECT count(*) INTO n FROM new_tag;
  IF n <> {len(rows)} THEN RAISE EXCEPTION '부여 행이 {len(rows)}개여야 하는데 %개다', n; END IF;

  SELECT count(*) INTO n FROM recipes;
  IF n <> {len(details)} THEN
    RAISE EXCEPTION '감사 스냅샷은 레시피 {len(details)}개인데 %개다. 카탈로그가 바뀌었으니 다시 뽑을 것', n;
  END IF;

  SELECT count(*) INTO n FROM new_tag t
   WHERE NOT EXISTS (SELECT 1 FROM recipes r WHERE r.id = t.recipe_id);
  IF n <> 0 THEN RAISE EXCEPTION '대상 레시피 %건이 존재하지 않는다', n; END IF;

  -- 사전에 있고, 부여 가능한(파생이 아닌) 태그여야 한다.
  SELECT count(*) INTO n FROM new_tag t
   WHERE NOT EXISTS (
     SELECT 1 FROM tags g
      WHERE g.code = t.tag_code AND g.axis_code = t.axis_code AND g.match_rule IS NULL);
  IF n <> 0 THEN
    RAISE EXCEPTION '사전에 없거나 파생 태그인 부여가 %건 있다', n;
  END IF;

  -- 배타축은 레시피당 하나다. 이미 붙어 있으면 이 백필이 처음이 아니라는 뜻이라 멈춘다
  -- (부분 유니크 인덱스에 걸려 어차피 실패하지만, 원인을 분명히 말하고 죽는 편이 낫다).
  SELECT count(*) INTO n
    FROM recipe_tags e JOIN new_tag t
      ON e.recipe_id = t.recipe_id AND e.axis_code = t.axis_code
   WHERE t.axis_code IN ('CUISINE', 'DISH', 'METHOD');
  IF n <> 0 THEN
    RAISE EXCEPTION '대상 레시피에 배타축 태그가 이미 %건 붙어 있다. 재실행인지 확인할 것', n;
  END IF;
END
$backfill$;

-- ── 적용 ─────────────────────────────────────────────────────────────────────
INSERT INTO recipe_tags (recipe_id, tag_code, axis_code, assigned_by)
SELECT recipe_id, tag_code, axis_code, '{ASSIGNED_BY}' FROM new_tag;

-- ── 검증 ─────────────────────────────────────────────────────────────────────
DO $verify$
DECLARE n BIGINT;
BEGIN
  SELECT count(*) INTO n FROM recipe_tags WHERE assigned_by = '{ASSIGNED_BY}';
  IF n <> {len(rows)} THEN RAISE EXCEPTION '반영 후 {ASSIGNED_BY} 부여가 {len(rows)}행이어야 하는데 %행이다', n; END IF;
""")
    for code in codes:
        out(f"""
  SELECT count(*) INTO n FROM recipe_tags WHERE tag_code = '{code}';
  IF n <> {per_code[code]} THEN RAISE EXCEPTION '{code} 가 {per_code[code]}행이어야 하는데 %행이다', n; END IF;""")
    out("""

  -- 배타축 중복이 없어야 한다(인덱스가 막지만 확인은 남긴다).
  SELECT count(*) INTO n FROM (
    SELECT recipe_id, axis_code FROM recipe_tags
     WHERE axis_code IN ('CUISINE', 'DISH', 'METHOD')
     GROUP BY recipe_id, axis_code HAVING count(*) > 1) dup;
  IF n <> 0 THEN RAISE EXCEPTION '배타축이 중복된 레시피가 %건 있다', n; END IF;
END
$verify$;

COMMIT;
""")
    sys.stderr.write(f"부여 {len(rows)}행 / {len(assignments)}레시피\n")
    for code in codes:
        sys.stderr.write(f"  {code:<20} {per_code[code]}\n")


if __name__ == "__main__":
    main()
