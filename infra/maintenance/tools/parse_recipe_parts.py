"""식품안전나라 COOKRCP01 의 RCP_PARTS_DTLS 를 (그룹, 이름, 수량, 단위) 로 파싱한다.

원문에 일관된 문법이 없어서, 확실하게 읽히는 것만 통과시키고 나머지는 uncertain 으로 떨군다.
uncertain 이 하나라도 있는 레시피는 운영 반영 대상에서 제외한다.
"""
import re
import unicodedata

FRACTIONS = {
    "½": "1/2", "⅓": "1/3", "⅔": "2/3", "¼": "1/4", "¾": "3/4",
    "⅕": "1/5", "⅖": "2/5", "⅗": "3/5", "⅘": "4/5",
    "⅙": "1/6", "⅚": "5/6", "⅛": "1/8", "⅜": "3/8", "⅝": "5/8", "⅞": "7/8",
}

# 계량 약어 → 한글 (기존 카탈로그 정리에서 정한 정책과 동일하게 맞춘다)
UNIT_ALIAS = {
    "Ts": "큰술", "T": "큰술", "tbsp": "큰술", "TS": "큰술",
    "ts": "작은술", "t": "작은술", "tsp": "작은술",
    "L": "L", "l": "ml",
}
UNITS = [
    "g", "ml", "kg", "L", "cc",
    "큰술", "작은술", "티스푼", "테이블스푼",
    "개", "장", "마리", "쪽", "대", "알", "줌", "봉", "모", "공기", "컵", "조각",
    "뿌리", "줄기", "송이", "통", "포기", "단", "kg", "cm", "인분", "팩", "캔", "켭",
]
UNIT_RE = "|".join(sorted({re.escape(u) for u in UNITS} | {re.escape(k) for k in UNIT_ALIAS}, key=len, reverse=True))

VAGUE = ["적당량", "적당히", "약간", "조금", "기호에 따라", "취향껏"]

# 그룹 머리말로 쓰이는 낱말. 콜론 없이 홀로 쓰이는 경우까지 잡아야 한다.
GROUP_WORDS = [
    "주재료", "재료", "부재료", "양념", "양념장", "양념소스", "밑간", "밑간양념",
    "소스", "소스소개", "드레싱", "고명", "국물", "육수", "완자", "반죽", "반죽재료",
    "속재료", "밥양념", "무침양념", "초간장", "곁들임", "장식", "기타", "튀김옷",
    "비빔밥", "샐러드", "조림소스", "볶음양념", "구이양념", "찜양념",
]
GROUP_WORD_RE = "|".join(sorted(map(re.escape, GROUP_WORDS), key=len, reverse=True))


def _norm(text):
    t = unicodedata.normalize("NFC", text)
    t = re.sub(r"<\s*br\s*/?\s*>", "\n", t, flags=re.I)
    t = re.sub(r"&nbsp;?", " ", t)
    t = re.sub(r"<[^>]{1,20}>", " ", t)
    for k, v in FRACTIONS.items():
        t = t.replace(k, v)
    t = t.replace("：", ":").replace("，", ",").replace("ⅹ", "x").replace("×", "x")
    # 유니코드 조합 단위 기호 → ASCII
    for a, b in (("㎖", "ml"), ("㎕", "ml"), ("㎗", "ml"), ("ℓ", "L"), ("㎏", "kg"),
                 ("㎉", "kcal"), ("㎝", "cm"), ("㎜", "mm"), ("㏄", "cc"), ("g", "g")):
        t = t.replace(a, b)
    t = re.sub(r"[·•▪]", " ", t)
    t = re.sub(r"[ \t]+", " ", t)
    return t


def _strip_headers(line):
    """한 줄에서 그룹 머리말을 떼어내고 [(그룹명, 본문), ...] 로 쪼갠다."""
    out = []
    # [주재료] / [ 2인분 ] / (반죽재료) 형태의 대괄호·괄호 머리말
    line = re.sub(r"\[\s*(\d+\s*인분[^\]]*)\s*\]", " ", line)
    line = re.sub(r"^\s*\(\s*(" + GROUP_WORD_RE + r")\s*\)\s*", r"\1: ", line)
    line = re.sub(r"\[\s*(" + GROUP_WORD_RE + r")\s*\]\s*:?", r" \1: ", line)
    line = re.sub(r"\[\s*소스소개\s*", " 소스: ", line)
    line = re.sub(r"^\s*\d+\s*인분\s*기준\s*$", "", line)
    line = re.sub(r"^\s*\d+\s*인분\s*기준\s*", "", line)

    # 문장 중간/앞머리의 "- 주재료 :", "● 국물 :", "고추장양념 :", "떡갈비 :" 형태.
    # 이 데이터셋에서 콜론은 언제나 그룹 구분자다(재료명에는 콜론이 안 들어간다).
    # 그래서 특정 낱말 목록에 의존하지 않고, 콜론 앞의 숫자 없는 낱말 1~2개를 머리말로 본다.
    pos = 0
    cur_group = None
    for m in re.finditer(r":", line):
        before = line[pos:m.start()]
        if re.fullmatch(r"\s*\d+\s*", before):      # 1:2 같은 비율 표기는 건너뛴다
            continue
        toks = before.split()
        head_toks = []
        while toks and len(head_toks) < 2 and not re.search(r"[\d~〜]", toks[-1]) and len(toks[-1]) <= 14:
            head_toks.insert(0, toks.pop())
        if not head_toks:
            continue
        seg = " ".join(toks).strip(" ,-–—●○▶")
        if seg:
            out.append((cur_group, seg))
        cur_group = " ".join(head_toks).strip(" ,-–—●○▶")
        pos = m.end()
    tail = line[pos:].strip(" ,")
    if tail:
        out.append((cur_group, tail))

    # 콜론 없이 낱말만으로 그룹을 나누는 형태도 있다 —
    #   '재료 검은깨(8g), ... 방울토마토(20g) 소스 통계피(20g)'
    #
    # 인정 위치를 세그먼트 맨 앞과 닫는 괄호 뒤로만 제한한다. 앞에 공백만 있으면
    # 인정하던 초기 버전은 '멸치 국물 100g' 을 '멸치'(수량 없음) + 고아 '100g' 으로
    # 쪼개 버렸다 — 재료명 끝에 그룹 낱말이 들어간 경우(다시마 국물, 닭 육수)를
    # 머리말로 오인한 것이다. 못 고치고 남기는 편이 수량을 깨뜨리는 것보다 낫다.
    bare = re.compile(r"(?:^|(?<=\)))\s*(" + GROUP_WORD_RE + r")(?=\s+\S)")
    expanded = []
    for group, seg in out:
        pos2 = 0
        g2 = group
        for m in bare.finditer(seg):
            if m.start() == 0 and pos2 == 0:
                g2 = m.group(1)
                pos2 = m.end()
                continue
            piece = seg[pos2:m.start()].strip(" ,")
            if piece:
                expanded.append((g2, piece))
            g2 = m.group(1)
            pos2 = m.end()
        rest = seg[pos2:].strip(" ,")
        if rest:
            expanded.append((g2, rest))
    return expanded


def _split_items(seg):
    """괄호 안의 쉼표는 건드리지 않고 쉼표로 자른다. 괄호 짝이 안 맞아도 음수로 안 내려간다."""
    items, buf, depth = [], [], 0
    for ch in seg:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth = max(0, depth - 1)
        if ch == "," and depth == 0:
            items.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    items.append("".join(buf))
    return [i.strip(" .") for i in items if i.strip(" .")]


def _num(s):
    s = s.strip()
    if re.fullmatch(r"\d+\s+\d+/\d+", s):           # "1 1/2"
        a, b = s.split()
        n, d = b.split("/")
        return float(a) + float(n) / float(d)
    if re.fullmatch(r"\d+/\d+", s):
        n, d = s.split("/")
        return float(n) / float(d)
    if re.fullmatch(r"\d+(?:\.\d+)?", s):
        return float(s)
    return None


def _parse_qty(q):
    """수량 문자열 → (amount, unit, ok). ok=False 면 애매한 것."""
    q = q.strip(" .")
    if not q:
        return None, None, True
    for v in VAGUE:
        if v in q:
            return None, "적당량" if "적당" in v or "기호" in v or "취향" in v else "약간", True
    if re.search(r"[~〜]|이상|정도", q):             # 3~4개 같은 범위: 정책상 손대지 않는다
        return None, None, False
    m = re.fullmatch(r"(\d+(?:\.\d+)?|\d+/\d+|\d+\s+\d+/\d+)\s*(" + UNIT_RE + r")?", q)
    if not m:
        return None, None, False
    amt = _num(m.group(1))
    if amt is None:
        return None, None, False
    unit = m.group(2)
    if unit is None:
        unit = "g"                                   # 원문에 단위 없음 → g (2026-08-17 단위 백필 근거와 동일)
    unit = UNIT_ALIAS.get(unit, unit)
    return amt, unit, True


_TRAIL_QTY = re.compile(
    r"^(?P<name>.*?)\s*(?P<num>\d+(?:\.\d+)?|\d+\s+\d+/\d+|\d+/\d+)\s*(?P<unit>" + UNIT_RE + r")?$"
)


def _name_ok(name):
    """이름 자체의 건전성. 괄호 안 치수(다시마(5x5cm))는 허용하되, 괄호 밖에
    숫자나 범위 기호가 남아 있으면 수량을 잘못 끊은 것이므로 거부한다."""
    if not name or re.search(r"[:\[\]]", name) or name.count("(") != name.count(")"):
        return False
    outside = re.sub(r"\([^()]*\)", "", name)
    # 이름 앞쪽 숫자는 정상('2가지색 파프리카'). 끝에 숫자·범위가 남았으면 수량을 잘못 끊은 것.
    return not re.search(r"[\d~〜]\s*(" + UNIT_RE + r")?\s*$", outside)


def _finish(name, amt, unit):
    name = re.sub(r"\s+", " ", name).strip(" .,:")
    unit = UNIT_ALIAS.get(unit, unit) if unit else unit
    return name, amt, unit, _name_ok(name)


def _parse_item(item):
    """'양파(20g)' / '연두부 75g(3/4모)' / '황태(채) 15g(10개)' / '소금적당량'
    → (name, amount, unit, ok)

    우선순위가 중요하다. 괄호 앞에 이미 수량이 있으면 그것이 주 수량이고, 뒤 괄호는
    대체 계량(3/4모)이나 치수(2x1cm)이므로 버린다. 그 반대로 읽으면 '양파 10g(2x1cm)' 의
    수량이 사라진다.
    """
    it = item.strip(" .")
    if not it:
        return None

    # 맨 끝 괄호 하나만 떼어 본다(중첩·연속 괄호 대응).
    m = re.fullmatch(r"(?P<head>.*)\((?P<inner>[^()]*)\)\.?", it, flags=re.S)
    head, inner = (m.group("head").strip(), m.group("inner").strip()) if m else (None, None)

    # (a) 괄호 앞이 이미 "이름 + 수량" 으로 끝나면 그쪽이 주 수량이다.
    if head:
        q = _TRAIL_QTY.fullmatch(head)
        if q and q.group("name").strip():
            amt = _num(q.group("num"))
            if amt is not None:
                return _finish(q.group("name"), amt, q.group("unit") or "g")

    # (b) 괄호 안이 수량이면 그것을 쓴다.
    #     쉼표로 여러 조각이면 질량·부피(g/ml/kg/L) 조각을 우선한다 —
    #     '저염베이컨(46g, 3장)' 의 주 수량은 46g 이고 3장은 대체 계량이다.
    #     질량·부피가 없으면 마지막 조각을 쓴다('다시마(5x5cm, 2장)' → 2장).
    if head and inner is not None:
        parts = _split_items(inner) if "," in inner else [inner]
        pick = None
        if len(parts) > 1:
            for idx, p in enumerate(parts):
                a, u, o = _parse_qty(p)
                if o and u in ("g", "ml", "kg", "L", "cc") and a is not None:
                    pick = idx
                    break
        if pick is None:
            pick = len(parts) - 1
        amt, unit, ok = _parse_qty(parts[pick])
        if ok and (amt is not None or unit is not None):
            rest = [p for i, p in enumerate(parts) if i != pick]
            name = head if not rest else f"{head}({', '.join(rest)})"
            return _finish(name, amt, unit)
        # 괄호 안이 재료 목록이면(물녹말(녹말가루 10g, 물 10g)) 애매한 것으로 넘긴다.
        if len(parts) > 1 and all(_TRAIL_QTY.fullmatch(p) for p in parts):
            return it, None, None, False

    # (c) 괄호 없이 "이름 + 수량"
    q = _TRAIL_QTY.fullmatch(it)
    if q and q.group("name").strip():
        amt = _num(q.group("num"))
        if amt is not None:
            return _finish(q.group("name"), amt, q.group("unit") or "g")

    # (d) 적당량·약간
    for v in VAGUE:
        if v in it:
            name = it.replace(v, "").strip(" ()")
            if name:
                return _finish(name, None, "적당량" if ("적당" in v or "기호" in v or "취향" in v) else "약간")

    # (e) 수량 없는 재료명 단독
    if not re.search(r"\d", it) and it.count("(") == it.count(")"):
        return _finish(it, None, None)

    return it, None, None, False


def parse(parts_text):
    """→ (items, problems). items = [(group, name, amount, unit)]"""
    items, problems = [], []
    text = _norm(parts_text)
    for line in text.split("\n"):
        line = line.strip()
        if not line:
            continue
        for group, seg in _strip_headers(line):
            for raw in _split_items(seg):
                if re.fullmatch(r"\d+\s*인분(\s*기준)?", raw):
                    continue
                if raw in GROUP_WORDS:
                    continue
                got = _parse_item(raw)
                if got is None:
                    continue
                name, amt, unit, ok = got
                name = re.sub(r"\s+", " ", name).strip(" .:")
                if not ok:
                    problems.append(raw)
                    continue
                if not name or re.search(r"[:\[\]]", name) or name.count("(") != name.count(")"):
                    problems.append(raw)
                    continue
                items.append((group, name, amt, unit))
    if not items:
        problems.append("<재료 0건>")
    return items, problems
