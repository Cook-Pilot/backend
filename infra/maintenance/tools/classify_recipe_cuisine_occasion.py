"""문화권(CUISINE)·용도(OCCASION) 태그를 부여한다.

원문(RCP_PAT2/RCP_WAY2)에서 유도할 수 있는 음식형태·조리법과 달리, 이 두 축은
원문에도 DB 에도 값이 없다. 그래서 **제목·요리종류·1인분 열량으로 판단한다.**

원칙: 확신이 서는 것만 붙인다. 애매하면 비운다.
잘못 붙은 태그는 없는 것보다 나쁘다 — 누르면 엉뚱한 목록이 나온다.
그래서 문화권은 1,150건 중 533건을 일부러 비워 뒀다.

입력 (full.json) 은 fetch_recipe_details.py 가 만든다. 운영 DB 덤프가 아니라
**공개 API** 로 받는다 — 이 축들은 제목·설명·요리종류만 보므로 재료 상세가 필요 없고,
목록·상세 API 는 게스트에 열려 있다(#76).

    python3 fetch_recipe_details.py          # → full.json
    python3 classify_recipe_cuisine_occasion.py   # → assign.json + 집계 출력

실제 반영 SQL 은 applied/ 에 있다. 2026-08-21 에 운영에 적용했다.
"""
import json, re

rows = json.load(open('full.json'))

# 짧은 단어는 다른 단어 안에 숨는다 — '포'가 '포크'에, '수육'이 '탕수육'에 걸린다.
# 그래서 단어마다 "이 문자열 안에 있으면 매칭이 아니다"라는 예외를 함께 둔다.
TRAPS = {
    "포":   ["포크", "포장", "포도", "포기"],
    "수육": ["탕수육"],
    "롤":   ["롤링"],
    "전":   ["전복", "전자", "전골", "전체"],
    "떡":   ["떡볶이"],
}

def has(text, words):
    for w in words:
        if w not in text:
            continue
        traps = TRAPS.get(w)
        if traps and all(text.count(w) <= text.count(t) for t in traps if t in text):
            continue
        return True
    return False

# ── 문화권 ───────────────────────────────────────────────
JP = ["초밥","스시","돈카츠","돈까스","우동","라멘","소바","텐동","오코노미","사시미","regola",
      "가라아게","야키","미소국","미소 된장","낫토","유부초밥","가츠동"]
CN = ["탕수","깐풍","짜장","짬뽕","마파","유린기","딤섬","춘권","팔보","라조","고추잡채"]
WE = ["파스타","스파게티","피자","리조또","리조토","스테이크","그라탕","그라탱","라자냐","뇨끼",
      "까르보나라","까르보","크로켓","오믈렛","오므라이스","샌드위치","버거","타코","또띠아",
      "베이글","바게트","바케트","깜빠뉴","치아바타","스콘","머핀","케이크","파이","무스","젤리",
      "파나코타","파르페","푸딩","브라우니","팬케이크","팬케익","와플","라떼","스프","수프",
      "포타주","가스파초","라따뚜이","후무스","빠에야","파니니","경단"]
AS = ["니고랭","나시고랭","쌀국수","팟타이","분짜","월남쌈","똠얌","반미","사테","커리","탄두리","코코넛밀크"]
KR = ["김치","된장","고추장","젓갈","나물","무침","겉절이","장아찌","조림","찜","전골","찌개",
      "국밥","비빔밥","쌈밥","주먹밥","떡갈비","불고기","제육","갈비","삼계","육개장","순두부",
      "청국장","묵","전","떡","약식","식혜","수정과","산적","잡채","보쌈","수육","곰탕","해장",
      "미역국","김밥","떡볶이","닭갈비","비빔","숙채","강정","자반","깍두기","동치미","시래기","볶음밥","말이","쌈","숙회","적"]

# 규칙으로 안 잡히는데 판단은 분명한 것. 지금 한 건뿐이라 목록으로 둔다 —
# 늘어나면 규칙을 고칠 신호다.
CUISINE_OVERRIDE = {
    "단호박견과류샐러드": "CUISINE_WESTERN",
}

def cuisine(r):
    t = r["title"]
    if t in CUISINE_OVERRIDE:
        return CUISINE_OVERRIDE[t]
    # 퓨전이 먼저다 — 한식 요소와 외국 요리가 함께 있으면 퓨전이다.
    # 중식 요리명은 그 자체로 중식이다 — '고추잡채'의 '잡채'는 한식 신호가 아니다.
    if has(t, CN):
        return "CUISINE_CHINESE"
    foreign = has(t, JP) or has(t, WE) or has(t, AS)
    korean = has(t, KR)
    if foreign and korean:
        return "CUISINE_FUSION"
    if has(t, JP): return "CUISINE_JAPANESE"
    if has(t, CN): return "CUISINE_CHINESE"
    if has(t, WE): return "CUISINE_WESTERN"
    if has(t, AS): return "CUISINE_ASIAN"
    if korean:     return "CUISINE_KOREAN"
    return None

# ── 용도 ────────────────────────────────────────────────
def kcal_of(r):
    m = re.search(r'([\d.]+)kcal', r["description"])
    return float(m.group(1)) if m else None

def dish_of(r):
    m = re.search(r'요리종류:\s*([^|\n]+)', r["description"])
    return m.group(1).strip() if m else None

SNACK_EXTRA = ["과자","칩","쿠키","강정","경단","라떼","주스","스무디","젤리","푸딩","빵",
               "떡","브레드","베이글","스콘","머핀","소보로","크래커","비스킷"]
HANGOVER = ["해장","북어","황태","콩나물국","선지","우거지","시래기국","복국","대구지리","조개탕","재첩"]
DRINK = ["튀김","강정","꼬치","포","육포","마른","맥주","닭발","골뱅이","오징어채","땅콩","안주"]
GUEST = ["갈비찜","전골","보쌈","구절판","신선로","삼합","한상","코스","잡채","갈비구이","찜닭"]
HOLIDAY = ["산적","잡채","약식","송편","전유어","동그랑땡","나박","식혜","수정과","떡국","만둣국","녹두전","동태전"]
KIDS = ["아이","어린이","유아","이유식","아기"]
LUNCHBOX = ["주먹밥","김밥","밥버거","도시락","롤"]
SOLO = ["1인","혼밥","간단","한그릇","한 그릇"]
LATE = ["야식","라면","떡볶이","치킨","족발","곱창"]

def occasions(r):
    t, out = r["title"], set()
    dish, k = dish_of(r), kcal_of(r)

    # 간식 — 후식은 정의상 간식이다.
    if dish == "후식" or has(t, SNACK_EXTRA):
        out.add("OCCASION_SNACK")
    # 다이어트 — 1인분 150kcal 미만. 중앙값(202)보다 확실히 낮은 구간만.
    # 1인분 100kcal 미만. 150 이면 402건, 120 이면 269건이라 "거의 전부"에 가까워
    # 필터 구실을 못 한다. 후식은 저열량이어도 간식이지 다이어트식이 아니다.
    if k is not None and k < 100 and dish != "후식":
        out.add("OCCASION_DIET")
    # 해장 — 국물 요리 중 해장 재료가 든 것.
    if has(t, HANGOVER) and dish in ("국&찌개", "밥"):
        out.add("OCCASION_HANGOVER")
    # 안주
    if has(t, DRINK):
        out.add("OCCASION_DRINKING_SNACK")
    # 손님상
    if has(t, GUEST):
        out.add("OCCASION_GUEST")
    # 명절
    if has(t, HOLIDAY):
        out.add("OCCASION_HOLIDAY")
    # 아이반찬은 붙이지 않는다. 제목에 '아이'가 든 6건만 잡히는데, 그 정도로는
    # 칩을 눌렀을 때 한 화면도 못 채운다. 순한 반찬을 가르는 규칙은 못 만들었다.
    # 도시락 — 식어도 먹을 수 있는 형태.
    if has(t, LUNCHBOX):
        out.add("OCCASION_LUNCHBOX")
    # 야식
    if has(t, LATE):
        out.add("OCCASION_LATE_NIGHT")
    return out

result = []
for r in rows:
    c = cuisine(r)
    o = occasions(r)
    if c or o:
        result.append({"id": r["id"], "title": r["title"], "cuisine": c, "occasions": sorted(o)})
json.dump(result, open('assign.json','w'), ensure_ascii=False, indent=1)

from collections import Counter
cc = Counter(x["cuisine"] for x in result if x["cuisine"])
oc = Counter(t for x in result for t in x["occasions"])
print("■ 문화권")
for k,v in cc.most_common(): print(f"   {k:20} {v:5}")
print(f"   {'(미부여)':20} {1150-sum(cc.values()):5}")
print("\n■ 용도")
for k,v in oc.most_common(): print(f"   {k:26} {v:5}")
print(f"\n태그가 하나라도 붙은 레시피: {len(result)}/1150")
