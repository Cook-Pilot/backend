"""extract_recipe_tags.assign 의 판정 규칙 테스트.

원문도 DB 도 필요 없다. 이 로직에서 틀리면 운영에 잘못된 태그가 들어가므로,
실제 데이터를 받기 전에 규칙만 따로 굳혀 둔다.

    python3 test_extract_recipe_tags.py
"""
from extract_recipe_tags import assign


def check(name, condition):
    print(("  OK   " if condition else "  FAIL ") + name)
    return condition


def src(seq, title, pat2, way2):
    return {"RCP_SEQ": seq, "RCP_NM": title, "RCP_PAT2": pat2, "RCP_WAY2": way2}


def db(rid, title):
    return {"id": rid, "title": title}


def tags_of(assignments, recipe_id):
    for a in assignments:
        if a["recipe_id"] == recipe_id:
            return {t["code"] for t in a["tags"]}
    return set()


ok = True

# 1. 평범한 한 건 — 두 축 모두 부여된다.
a, s, r = assign([src("1", "새우 두부 계란찜", "반찬", "찌기")], [db("r1", "새우 두부 계란찜")])
ok &= check("두 축 모두 매핑된다", tags_of(a, "r1") == {"DISH_SIDE", "METHOD_STEAM"})

# 2. '기타'는 미부여. 다른 축은 살아남는다.
a, s, r = assign([src("2", "방울토마토 소박이", "반찬", "기타")], [db("r2", "방울토마토 소박이")])
ok &= check("기타는 미부여이고 다른 축은 남는다", tags_of(a, "r2") == {"DISH_SIDE"})
ok &= check("기타가 집계된다", r["skipped_other"]["METHOD"] == 1)

# 3. '국&찌개' — 원문은 &, 태그 라벨은 · 라서 표기를 헷갈리기 쉽다.
a, _, _ = assign([src("3", "사과 새우 북엇국", "국&찌개", "끓이기")], [db("r3", "사과 새우 북엇국")])
ok &= check("국&찌개가 DISH_SOUP_STEW 로 간다", "DISH_SOUP_STEW" in tags_of(a, "r3"))

# 4. 제목 중복 — 축별로 따로 판단한다.
dup = [src("4", "겹치는 이름", "반찬", "굽기"), src("5", "겹치는 이름", "반찬", "볶기")]
a, s, r = assign(dup, [db("r4", "겹치는 이름")])
ok &= check("제목이 겹쳐도 값이 같은 축은 부여한다", tags_of(a, "r4") == {"DISH_SIDE"})
ok &= check("값이 갈리는 축만 보류한다", r["skipped_ambiguous"]["METHOD"] == 1)

# 5. 제목이 원문에 없으면 아무것도 부여하지 않는다.
a, s, r = assign([src("6", "있는 이름", "반찬", "굽기")], [db("r5", "없는 이름")])
ok &= check("미매칭은 부여하지 않는다", a == [])
ok &= check("미매칭이 집계된다", r["unmatched_title"] == ["없는 이름"])

# 6. 사전에 없는 원문 값은 조용히 넘기지 않고 센다.
a, s, r = assign([src("7", "새 분류", "새로운값", "굽기")], [db("r6", "새 분류")])
ok &= check("모르는 값은 부여하지 않는다", tags_of(a, "r6") == {"METHOD_GRILL"})
ok &= check("모르는 값이 보고된다", r["unknown_value"]["DISH:새로운값"] == 1)

# 7. 앞뒤 공백은 매칭을 막지 않는다.
a, _, _ = assign([src("8", " 공백 있는 제목 ", "밥", "볶기")], [db("r7", "공백 있는 제목")])
ok &= check("제목 공백은 무시한다", tags_of(a, "r7") == {"DISH_RICE", "METHOD_STIR_FRY"})


# 8. 출처 매핑(#85): 제목이 유일하면 RCP_SEQ 를 기록한다.
a, s, r = assign([src("21", "출처 기록 확인", "반찬", "굽기")], [db("r21", "출처 기록 확인")])
ok &= check("제목이 유일하면 출처가 기록된다",
            s == [{"recipe_id": "r21", "rcp_seq": "21"}] and r["source_ref_recorded"] == 1)

# 9. 제목이 겹치면 출처는 비워 둔다 — 어느 원문 행인지 정할 근거가 없다.
dup_src = [src("22", "겹치는 출처", "반찬", "굽기"), src("23", "겹치는 출처", "반찬", "굽기")]
a, s, r = assign(dup_src, [db("r22", "겹치는 출처")])
ok &= check("제목이 겹치면 출처를 보류한다", s == [] and r["source_ref_dup_title"] == 1)
ok &= check("겹쳐도 값이 같은 축의 태그는 남는다", tags_of(a, "r22") == {"DISH_SIDE", "METHOD_GRILL"})

# 10. 원문에 번호가 없으면 기록하지 않고 센다.
a, s, r = assign([{"RCP_NM": "번호 없음", "RCP_PAT2": "반찬", "RCP_WAY2": "굽기"}],
                 [db("r23", "번호 없음")])
ok &= check("RCP_SEQ 가 없으면 출처를 건너뛰고 센다",
            s == [] and r["source_ref_missing_seq"] == 1)

# 11. DB 쪽 제목 중복 — 같은 제목의 레시피가 DB 에 둘이면 같은 RCP_SEQ 가 두 행에 들어가
#     uq_recipes_source 부분 UNIQUE 에 걸린다. 출처만 보류하고 태그는 양쪽에 남긴다.
a, s, r = assign([src("30", "디비 중복", "반찬", "굽기")],
                 [db("r30", "디비 중복"), db("r31", "디비 중복")])
ok &= check("DB 제목이 겹치면 출처를 보류한다", s == [] and r["source_ref_dup_db_title"] == 2)
ok &= check("DB 제목이 겹쳐도 태그는 양쪽에 남는다",
            tags_of(a, "r30") == {"DISH_SIDE", "METHOD_GRILL"}
            and tags_of(a, "r31") == {"DISH_SIDE", "METHOD_GRILL"})

print("\n전체 통과" if ok else "\n실패 있음")
raise SystemExit(0 if ok else 1)
