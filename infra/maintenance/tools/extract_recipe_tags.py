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
from collections import defaultdict

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
    """(부여 목록, 리포트) 를 돌려준다. 파일도 DB 도 건드리지 않는 순수 함수다."""
    by_title = defaultdict(list)
    for row in source_rows:
        by_title[row["RCP_NM"].strip()].append(row)

    assignments = []
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
    }

    for recipe in db_recipes:
        title = recipe["title"].strip()
        rows = by_title.get(title)
        if not rows:
            report["unmatched_title"].append(recipe["title"])
            continue
        report["matched"] += 1

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

    return assignments, report


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
    if report["unmatched_title"]:
        out(f"제목이 원문에 없는 레시피 {len(report['unmatched_title'])}건 (앞 5개): "
            f"{report['unmatched_title'][:5]}\n")


if __name__ == "__main__":
    source = json.load(open("source.json"))
    if isinstance(source, dict):          # API 응답을 그대로 저장한 경우
        source = source["COOKRCP01"]["row"]
    details = json.load(open("details.json"))

    assignments, report = assign(source, details)
    _print_report(report)

    with open("tag_assign.json", "w") as f:
        json.dump(assignments, f, ensure_ascii=False, indent=1)
    sys.stderr.write(f"\ntag_assign.json 에 {len(assignments)}개 레시피 기록\n")
