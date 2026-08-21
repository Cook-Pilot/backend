#!/usr/bin/env python3
"""공개 API 로 레시피 전량을 받아 full.json 을 만든다.

운영 DB 덤프(AWS CLI + SSM)가 필요 없다. 목록·상세 API 가 게스트에 열려 있어서(#76)
제목·설명·재료명·단계 수를 그대로 받을 수 있다. 태그 분류는 이만큼만 쓴다.

재료의 양·단위나 개인 데이터가 필요한 도구는 여전히 덤프를 써야 한다.

    BASE=http://13.209.243.235 python3 fetch_recipe_details.py
"""
import concurrent.futures as cf
import json
import os
import time
import urllib.request

BASE = os.environ.get("BASE", "http://13.209.243.235")
API = f"{BASE}/api/v1/recipes"


def fetch(summary):
    for _ in range(3):
        try:
            with urllib.request.urlopen(f"{API}/{summary['id']}", timeout=15) as response:
                detail = json.load(response)
            return {
                "id": summary["id"],
                "title": summary["title"],
                "description": summary["description"] or "",
                "ingredients": [i["name"] for i in detail["ingredients"]],
                "steps": len(detail["steps"]),
                "servings": detail.get("baseServings"),
            }
        except Exception:
            time.sleep(0.5)
    return None


if __name__ == "__main__":
    with urllib.request.urlopen(API, timeout=30) as response:
        summaries = json.load(response)

    # 동시 요청은 8 개로 묶는다 — 운영 서버를 상대로 도는 스크립트다.
    with cf.ThreadPoolExecutor(max_workers=8) as pool:
        rows = [r for r in pool.map(fetch, summaries) if r]

    missing = len(summaries) - len(rows)
    if missing:
        raise SystemExit(f"{missing}건을 받지 못했다. 부분 결과로 분류하면 안 된다.")

    json.dump(rows, open("full.json", "w"), ensure_ascii=False)
    print(f"full.json 에 {len(rows)}건 기록")
