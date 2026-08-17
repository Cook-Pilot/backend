import json, re, collections
from parse_parts import parse, GROUP_WORDS

det = json.load(open("details.json"))
src = {r["RCP_NM"]: r for r in json.load(open("source.json"))}
GW = "|".join(map(re.escape, GROUP_WORDS))


def polluted(n):
    out = re.sub(r"\([^()]*\)", "", n)
    return bool(
        "<br>" in n or ":" in n or "[" in n or "]" in n
        or n.count("(") != n.count(")")
        or n.strip() in GROUP_WORDS
        or re.search(r"[\d~]\s*$", out)
        or re.match(r"^(" + GW + r")\s+\S", n)
    )


def core(n):
    return re.sub(r"\s+", "", re.sub(r"\([^()]*\)", "", n)).strip()


aff = [r for r in det if any(polluted(i["name"]) for i in r["ingredients"])]
pol_rows = sum(1 for r in det for i in r["ingredients"] if polluted(i["name"]))

conf, defer = [], []
for r in aff:
    if r["title"] not in src:
        defer.append({"title": r["title"], "why": "원문 없음(수동 시드 레시피)"})
        continue
    items, probs = parse(src[r["title"]]["RCP_PARTS_DTLS"])
    resid = [n for g, n, a, u in items if polluted(n)]
    noqty = [n for g, n, a, u in items if a is None and u is None]
    if probs or resid or noqty:
        defer.append({"title": r["title"], "why": (probs[:2] + [f"잔존오염:{resid[:2]}"] * bool(resid)
                                                   + [f"수량없음:{noqty[:2]}"] * bool(noqty))})
    else:
        conf.append((r, items))

print(f"현재 오염:            {pol_rows}행 / {len(aff)}개 레시피")
print(f"  파서 확신 → 수정:   {len(conf)}개 레시피")
print(f"  보류:               {len(defer)}개 레시피")
resolved = sum(1 for r, _ in conf for i in r["ingredients"] if polluted(i["name"]))
print(f"  해소되는 오염 행:   {resolved} / {pol_rows}  (남는 오염 {pol_rows - resolved}행)")

# ── 안전성 검증 ────────────────────────────────────────────────
dec, inc, same, lost = [], 0, 0, []
rows_before = rows_after = 0
for r, items in conf:
    rows_before += len(r["ingredients"])
    rows_after += len(items)
    gc = sum(i["amount"] for i in r["ingredients"] if i["amount"] and i["unit"] == "g")
    gn = sum(a for g, n, a, u in items if a and u == "g")
    d = round(gn - gc, 2)
    if d < -0.01:
        dec.append((r["title"], gc, gn, d))
    elif d > 0.01:
        inc += 1
    else:
        same += 1
    cn = collections.Counter(core(i["name"]) for i in r["ingredients"] if not polluted(i["name"]))
    nn = collections.Counter(core(n) for g, n, a, u in items)
    for k, v in cn.items():
        if k and nn[k] < v:
            lost.append((r["title"], k, v, nn[k]))

print()
print(f"■ 행수: {rows_before} → {rows_after}  ({rows_after - rows_before:+d})")
print(f"■ 그램 총량: 증가 {inc} · 동일 {same} · 감소 {len(dec)}")
for x in sorted(dec, key=lambda y: y[3])[:10]:
    print("     감소:", x)
print(f"■ 오염 아닌 재료가 사라지는 건: {len(lost)}")
for l in lost[:10]:
    print("     ", l)

json.dump([{"id": r["id"], "title": r["title"],
            "items": [{"name": n, "amount": a, "unit": u} for g, n, a, u in items]}
           for r, items in conf], open("rebuild.json", "w"), ensure_ascii=False)
json.dump(defer, open("deferred.json", "w"), ensure_ascii=False)
print("\nrebuild.json / deferred.json 갱신")
