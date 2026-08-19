"""재료 그룹(양념장/소스/국물…)을 원문에서 다시 뽑아 현재 운영 행에 매칭한다.

두 경로로만 매칭한다. 둘 다 아니면 그룹을 비워 둔다(추측하지 않는다).
  A) 이름 복구로 재구성한 305개 레시피 — id 가 결정론적이라 정확히 계산된다.
  B) 그 밖의 레시피 — 파싱 결과가 현재 행과 (이름, 수량, 단위) 순서까지 완전히
     일치할 때만. 하나라도 어긋나면 그 레시피 전체를 건너뛴다.
"""
import json, uuid, re
from parse_recipe_parts import parse

NS = uuid.uuid5(uuid.NAMESPACE_URL, "https://cookpilot.app/recipe-ingredient")

det = json.load(open("details.json"))           # 이름 복구 반영 후 현재 상태
src = {r["RCP_NM"]: r for r in json.load(open("source.json"))}
rebuilt = {e["id"]: e for e in json.load(open("rebuild.json"))}

assign = {}          # ingredient_id -> group
stat = {"A": 0, "B": 0, "skip_no_src": 0, "skip_probs": 0, "skip_mismatch": 0}
grouped_rows = 0

for r in det:
    if r["title"] not in src:
        stat["skip_no_src"] += 1
        continue
    items, probs = parse(src[r["title"]]["RCP_PARTS_DTLS"])

    if r["id"] in rebuilt:
        # A) 재구성분: gen_ingredient_names_sql.py 와 같은 규칙으로 id 를 다시 만든다.
        exp = rebuilt[r["id"]]["items"]
        parsed = [(g, n, a, u) for g, n, a, u in items]
        if len(parsed) != len(exp):
            stat["skip_mismatch"] += 1
            continue
        okall = all(p[1] == e["name"] and p[2] == e["amount"] and p[3] == e["unit"]
                    for p, e in zip(parsed, exp))
        if not okall:
            stat["skip_mismatch"] += 1
            continue
        for n_, (g, name, a, u) in enumerate(parsed):
            iid = str(uuid.uuid5(NS, f"{r['id']}:{n_}:{name}"))
            if g:
                assign[iid] = g
                grouped_rows += 1
        stat["A"] += 1
        continue

    # B) 그 밖: 완전 일치할 때만
    if probs:
        stat["skip_probs"] += 1
        continue
    cur = r["ingredients"]
    if len(cur) != len(items):
        stat["skip_mismatch"] += 1
        continue
    if not all(c["name"] == n and c["amount"] == a and c["unit"] == u
               for c, (g, n, a, u) in zip(cur, items)):
        stat["skip_mismatch"] += 1
        continue
    for c, (g, n, a, u) in zip(cur, items):
        if g:
            assign[c["id"]] = g
            grouped_rows += 1
    stat["B"] += 1

# 현재 존재하는 id 인지 확인 (A 경로의 id 계산이 맞는지 검증)
live = {i["id"] for r in det for i in r["ingredients"]}
missing = [k for k in assign if k not in live]

print(f"레시피 매칭: 재구성분 {stat['A']}  그 밖 완전일치 {stat['B']}")
print(f"  건너뜀 — 원문없음 {stat['skip_no_src']}  애매 {stat['skip_probs']}  불일치 {stat['skip_mismatch']}")
print(f"그룹이 붙는 재료 행: {len(assign)}")
print(f"계산한 id 가 운영에 없는 건: {len(missing)}")
if missing:
    print("  예:", missing[:5])

import collections
print("\n그룹 값 상위 25:")
for g, c in collections.Counter(assign.values()).most_common(25):
    print(f"  {c:5d}  {g}")
print(f"\n서로 다른 그룹 값: {len(set(assign.values()))}개")

json.dump(assign, open("group_assign.json", "w"), ensure_ascii=False)
