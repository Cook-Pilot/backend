"""식품안전나라 COOKRCP01 원문을 받아 source.json 으로 저장한다.

카탈로그의 출처이자, 임포터가 repo 에 없어서 **운영 DB 를 대조할 수 있는 유일한 기준**이다.

    FOODSAFETY_API_KEY=... python3 fetch_recipe_source.py

**`sample` 키로는 안 된다.** 2026-08-19 확인 기준 sample 키는 요청 구간과 무관하게 늘 같은
5행(RCP_SEQ 28·29·31·32·33)만 돌려준다. total_count 는 1156 로 정상 보고하므로, 받은 행 수를
세지 않으면 조용히 5건짜리 결과를 만든다. 그래서 아래에서 전량 수신을 검사한다.

키는 공공데이터포털(data.go.kr)의 '식품의약품안전처 조리식품 레시피 DB' 에서 발급한다.
"""
import json
import os
import sys
import urllib.request

# 키가 URL 경로에 실리므로 반드시 TLS 로 보낸다(평문이면 키·응답이 그대로 노출된다).
BASE = "https://openapi.foodsafetykorea.go.kr/api"
SERVICE = "COOKRCP01"
PAGE = 1000          # 한 번에 요청할 구간 폭


def fetch(key, start, end):
    url = f"{BASE}/{key}/{SERVICE}/json/{start}/{end}"
    with urllib.request.urlopen(url, timeout=60) as response:
        body = json.load(response)
    payload = body[SERVICE]
    code = payload.get("RESULT", {}).get("CODE")
    if code not in (None, "INFO-000"):
        raise SystemExit(f"API 오류 {code}: {payload.get('RESULT', {}).get('MSG')}")
    return payload.get("row") or [], int(payload.get("total_count", 0))


def main():
    key = os.environ.get("FOODSAFETY_API_KEY")
    if not key:
        raise SystemExit("FOODSAFETY_API_KEY 가 필요하다. sample 키로는 5행만 나온다(모듈 설명 참고).")

    rows, total = fetch(key, 1, PAGE)
    sys.stderr.write(f"total_count={total}, 첫 구간 {len(rows)}행\n")

    start = PAGE + 1
    while len(rows) < total:
        page, _ = fetch(key, start, start + PAGE - 1)
        if not page:
            break
        rows.extend(page)
        sys.stderr.write(f"  {start}~{start + PAGE - 1}: {len(page)}행 (누적 {len(rows)})\n")
        start += PAGE

    if len(rows) != total:
        raise SystemExit(
            f"전량을 못 받았다: {len(rows)}/{total}. sample 키를 쓰고 있지 않은지 확인할 것.")

    # 개수 일치는 중복+누락이 상쇄된 경우를 못 잡는다. 고유번호가 겹치면
    # 다른 행이 빠졌다는 뜻이므로 경고가 아니라 실패다 — 이 파이프라인의
    # 검증은 '통과 아니면 정지' 둘뿐이어야 한다.
    seqs = {r.get("RCP_SEQ") for r in rows}
    if len(seqs) != len(rows):
        raise SystemExit(
            f"RCP_SEQ 가 중복된다({len(rows) - len(seqs)}건) — 페이지네이션이 어긋났다. 다시 받을 것.")

    with open("source.json", "w") as f:
        json.dump(rows, f, ensure_ascii=False)
    sys.stderr.write(f"source.json 에 {len(rows)}행 저장\n")


if __name__ == "__main__":
    main()
