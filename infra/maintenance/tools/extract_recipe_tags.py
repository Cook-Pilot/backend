"""원문(COOKRCP01)의 RCP_PAT2/RCP_WAY2 를 태그 부여로 옮긴다.

원문에 이미 있는 분류를 그대로 옮기는 것이라 **추론이 없다**. LLM 분류(문화권·용도)와 달리
이 구간만 결손 0·추측 0 으로 채울 수 있어서 먼저 한다.

매칭은 제목(RCP_NM)으로만 한다 — 임포터가 RCP_SEQ 를 저장하지 않아 다른 열쇠가 없다.
제목이 겹치는 원문 행이 있으면 **축별로** 판단한다: 그 그룹의 값이 전부 같으면 어느 행에
대응되든 결과가 같으므로 부여하고, 하나라도 다르면 그 축만 건너뛴다(추측하지 않는다).

`기타`는 태그로 만들지 않았으므로 미부여로 남긴다. 고르기 애매할 때 도망갈 칸을 주면
축이 죽는다는 것이 사전 설계의 전제다(만개의레시피 상황축 57%가 `일상` 한 값).

다른 도구와 달리 import 해도 부작용이 없다(파일 읽기는 __main__ 안에서만). 로직을 테스트에서
직접 부를 수 있게 하려는 것이고, test_extract_recipe_tags.py 가 그렇게 쓴다.
"""
import json
import sys
from collections import Counter, defaultdict

# 원문 값 → 사전 code. 원문 표기 그대로가 열쇠다('국&찌개'는 & 이고 태그 라벨의 '국·찌개'와 다르다).
DISH = {
    "반찬": "DISH_SIDE",
    "일품": "DISH_MAIN",
    "후식": "DISH_DESSERT",
    "밥": "DISH_RICE",
    "국&찌개": "DISH_SOUP_STEW",
}
METHOD = {
    "끓이기": "METHOD_BOIL",
    "굽기": "METHOD_GRILL",
    "볶기": "METHOD_STIR_FRY",
    "찌기": "METHOD_STEAM",
    "튀기기": "METHOD_DEEP_FRY",
}
AXES = [("DISH", "RCP_PAT2", DISH), ("METHOD", "RCP_WAY2", METHOD)]

SKIP_VALUES = {"기타", ""}


def assign(source_rows, db_recipes):
    """(부여 목록, 출처 매핑, 리포트) 를 돌려준다. 파일도 DB 도 건드리지 않는 순수 함수다.

    출처 매핑(#85): 어차피 제목 매칭을 하므로 그 부산물로 (recipe_id, RCP_SEQ) 쌍을
    기록한다. 제목이 원문과 DB 양쪽에서 유일할 때만 — 원문 쪽이 겹치면 어느 행인지 정할
    근거가 없고, DB 쪽이 겹치면 같은 번호가 두 레시피에 들어가 부분 UNIQUE 에 걸린다.
    이걸 기록해 두면 다음부터는 제목 매칭이 필요 없어진다(세 번째 삽질 방지).
    """
    by_title = defaultdict(list)
    for row in source_rows:
        by_title[row["RCP_NM"].strip()].append(row)
    db_title_counts = Counter(r["title"].strip() for r in db_recipes)

    assignments = []
    source_refs = []
    report = {
        "db_recipes": len(db_recipes),
        "source_rows": len(source_rows),
        "source_titles": len(by_title),
        "matched": 0,
        "unmatched_title": [],
        "assigned": defaultdict(int),
        "skipped_other": defaultdict(int),
        "skipped_ambiguous": defaultdict(int),
        "unknown_value": defaultdict(int),
        "source_ref_recorded": 0,
        "source_ref_dup_title": 0,
        "source_ref_dup_db_title": 0,
        "source_ref_missing_seq": 0,
    }

    for recipe in db_recipes:
        title = recipe["title"].strip()
        rows = by_title.get(title)
        if not rows:
            report["unmatched_title"].append(recipe["title"])
            continue
        report["matched"] += 1

        if len(rows) > 1:
            # 원문 쪽에서 제목이 겹치면 어느 행인지 확정할 수 없다 — 출처는 비워 둔다.
            report["source_ref_dup_title"] += 1
        elif db_title_counts[title] > 1:
            # DB 쪽에서 제목이 겹치면 같은 RCP_SEQ 가 두 레시피에 들어가
            # uq_recipes_source 부분 UNIQUE 에 걸린다. 여기서 보류하고 세는 편이
            # 반영 SQL 이 '유니크 위반'으로 죽는 것보다 원인이 분명하다.
            report["source_ref_dup_db_title"] += 1
        else:
            seq = (rows[0].get("RCP_SEQ") or "").strip()
            if seq:
                source_refs.append({"recipe_id": recipe["id"], "rcp_seq": seq})
                report["source_ref_recorded"] += 1
            else:
                report["source_ref_missing_seq"] += 1

        tags = []
        for axis, field, mapping in AXES:
            values = {(row.get(field) or "").strip() for row in rows}

            if len(values) > 1:
                # 제목이 겹치는 원문 행들이 이 축에서 서로 다르다. 어느 쪽인지 정할 근거가 없다.
                report["skipped_ambiguous"][axis] += 1
                continue

            value = values.pop()
            if value in SKIP_VALUES:
                report["skipped_other"][axis] += 1
                continue

            code = mapping.get(value)
            if code is None:
                # 사전에 없는 원문 값. 새 값이 생겼다는 뜻이라 조용히 넘기지 않고 센다.
                report["unknown_value"][f"{axis}:{value}"] += 1
                continue

            tags.append({"code": code, "axis": axis})
            report["assigned"][code] += 1

        if tags:
            assignments.append({"recipe_id": recipe["id"], "title": recipe["title"], "tags": tags})

    return assignments, source_refs, report


def _print_report(report):
    out = sys.stderr.write
    out(f"DB 레시피 {report['db_recipes']}건 / 원문 {report['source_rows']}행"
        f"(제목 {report['source_titles']}종)\n")
    out(f"제목 매칭 {report['matched']}건, 미매칭 {len(report['unmatched_title'])}건\n")
    for axis in ("DISH", "METHOD"):
        assigned = sum(v for k, v in report["assigned"].items() if k.startswith(axis))
        out(f"  {axis}: 부여 {assigned} / 기타·빈값 {report['skipped_other'][axis]}"
            f" / 제목중복으로 보류 {report['skipped_ambiguous'][axis]}\n")
    for code, n in sorted(report["assigned"].items()):
        out(f"    {code:<20} {n}\n")
    if report["unknown_value"]:
        out("사전에 없는 원문 값(사전을 늘려야 할 수 있다):\n")
        for key, n in sorted(report["unknown_value"].items()):
            out(f"    {key} {n}\n")
    out(f"출처(RCP_SEQ) 기록 {report['source_ref_recorded']}건"
        f" / 원문 제목 중복 보류 {report['source_ref_dup_title']}"
        f" / DB 제목 중복 보류 {report['source_ref_dup_db_title']}"
        f" / 원문에 번호 없음 {report['source_ref_missing_seq']}\n")
    if report["unmatched_title"]:
        out(f"제목이 원문에 없는 레시피 {len(report['unmatched_title'])}건 (앞 5개): "
            f"{report['unmatched_title'][:5]}\n")


if __name__ == "__main__":
    source = json.load(open("source.json"))
    if isinstance(source, dict):          # API 응답을 그대로 저장한 경우
        payload = source["COOKRCP01"]
        source = payload["row"]
        # fetch 를 거치지 않은 입력이라 전량 수신 검사를 여기서도 한다.
        # sample 키 응답(5행, total_count=1156)이 이 경로로 들어오면
        # 부분 백필이 조용히 만들어진다.
        total = int(payload.get("total_count", len(source)))
        if len(source) != total:
            raise SystemExit(
                f"source.json 이 부분 응답이다: {len(source)}/{total}행. "
                "fetch_recipe_source.py 로 전량을 다시 받을 것.")
    details = json.load(open("details.json"))

    assignments, source_refs, report = assign(source, details)
    _print_report(report)

    with open("tag_assign.json", "w") as f:
        json.dump(assignments, f, ensure_ascii=False, indent=1)
    with open("source_ref.json", "w") as f:
        json.dump(source_refs, f, ensure_ascii=False, indent=1)
    sys.stderr.write(f"\ntag_assign.json 에 {len(assignments)}개 레시피,"
                     f" source_ref.json 에 출처 {len(source_refs)}건 기록\n")
