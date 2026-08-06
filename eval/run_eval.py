#!/usr/bin/env python3
"""조리 중 AI 피드백 — 대화 맥락(멀티턴) 활용 품질 평가 러너.

무엇을 평가하나: "이전 대화 전체를 컨텍스트로 주입하면, 레시피에 없는 재료(대화 중
추가한 마늘)에 대한 후속 발화를 모델이 제대로 처리하는가".

왜 서버를 안 거치나: 현재 API(AiFeedbackRequest)에는 대화 이력 필드가 없다.
그래서 이 러너는 CookingCoachClient 의 시스템 프롬프트를 복제하고 스크립트된
대화(conversation.json)를 멀티턴으로 주입해 Gemini 를 직접 호출한다 —
즉 "이력을 넣어주는 미래 파이프라인"의 프롬프트 프로토타입 평가다.
API 에 이력 필드가 생기면 서버 블랙박스 호출로 되돌릴 것.

주의: SYSTEM_PROMPT / USER_PROMPT_TEMPLATE 는 CookingCoachClient.java 의 복사본이다.
서버 프롬프트를 바꾸면 여기도 같이 바꿔야 한다(드리프트 주의).

실행:
  GEMINI_API_KEY=... python3 eval/run_eval.py

환경변수:
  GEMINI_API_KEY      (필수) 대상 모델 + judge 호출용
  EVAL_TARGET_MODEL   기본 gemini-3.5-flash (서버 기본값과 동일)
  EVAL_JUDGE_MODEL    기본 gemini-3.1-pro-preview (대상과 다른 모델 유지. 3.5는 flash 만 존재)
  EVAL_TRIALS         태스크당 시행 횟수. 기본 3
  EVAL_FILTER         id/category 부분 일치 필터

결과: eval/results/<UTC타임스탬프>.jsonl 에 transcript 저장. 요약은 stdout.
"""

import json
import os
import re
import sys
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

EVAL_DIR = Path(__file__).parent
TRIALS = int(os.environ.get("EVAL_TRIALS", "3"))
TARGET_MODEL = os.environ.get("EVAL_TARGET_MODEL", "gemini-3.5-flash")
JUDGE_MODEL = os.environ.get("EVAL_JUDGE_MODEL", "gemini-3.1-pro-preview")
TASK_FILTER = os.environ.get("EVAL_FILTER", "")
API_KEY = os.environ.get("GEMINI_API_KEY", "")

GENAI_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

# ---- CookingCoachClient.java 복사본 (드리프트 주의) -------------------------

SYSTEM_PROMPT = """당신은 CookPilot의 조리 중 음성 어시스턴트입니다.
사용자는 지금 불 앞에 서 있고, 손이 젖어 있으며, 답변을 귀로만 듣습니다.

응답 규칙:
- 답변은 그대로 TTS 로 읽힙니다. 한국어 두 문장 이내, 숫자는 소리 내어 읽기 쉽게 쓰세요.
- 머리말·목록·마크다운 없이 문장만 쓰세요.
- 되묻지 마세요. 발화가 불분명하면 현재 단계 기준으로 가장 안전한 안내를 하세요.

안전 원칙(어길 수 없음):
- 변질이 의심되는 재료를 먹어도 된다고 단정하지 마세요.
- 덜 익은 육류·해산물은 추가 가열을 먼저 안내하세요.
- 알레르기 질문은 보수적으로 답하고 확신하지 마세요.
- 화상·화재 위험이 감지되면 다른 안내보다 안전 행동을 먼저 말하세요."""

USER_PROMPT_TEMPLATE = """요리: {title}
현재 단계 {step_index}: {instruction}
단계 타이머(초): {timer}
단계 주의사항: {caution}
사용자 발화(음성 인식 결과): {user_speech}"""


def http_json(url, payload):
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", "x-goog-api-key": API_KEY},
    )
    with urllib.request.urlopen(req, timeout=60) as res:
        return json.load(res)


def generate(model, contents, system=None, temperature=0.2, json_output=False):
    body = {"contents": contents, "generationConfig": {"temperature": temperature}}
    if system:
        body["systemInstruction"] = {"parts": [{"text": system}]}
    if json_output:
        # MimeType 만으로는 reason 안의 따옴표 등으로 JSON 이 깨질 수 있어 스키마로 강제한다.
        body["generationConfig"]["responseMimeType"] = "application/json"
        body["generationConfig"]["responseSchema"] = {
            "type": "OBJECT",
            "properties": {
                "pass": {"type": "BOOLEAN"},
                "reason": {"type": "STRING"},
            },
            "required": ["pass", "reason"],
        }
    res = http_json(GENAI_URL.format(model=model), body)
    return res["candidates"][0]["content"]["parts"][0]["text"].strip()


# ---- 대상 호출: 대화 이력 + 현재 발화 --------------------------------------

def call_target(context, task):
    step = context["recipe"]["steps"][context["current_step_index"]]
    final_prompt = USER_PROMPT_TEMPLATE.format(
        title=context["recipe"]["title"],
        step_index=step["step_index"],
        instruction=step["instruction"],
        timer=step["timer_seconds"] if step["timer_seconds"] is not None else "없음",
        caution=step["caution_note"] or "없음",
        user_speech=task["user_speech"],
    )
    contents = [
        {"role": turn["role"], "parts": [{"text": turn["text"]}]}
        for turn in context["history"]
    ]
    contents.append({"role": "user", "parts": [{"text": final_prompt}]})
    return generate(TARGET_MODEL, contents, system=SYSTEM_PROMPT)


# ---- 1단계: 코드 채점 (형식 규칙, 결정적) ----------------------------------

def code_grade(text):
    failures = []
    if not re.search(r"[가-힣]", text):
        failures.append("한국어 아님")
    # "1.5" 같은 소수점은 세지 않도록, 뒤에 숫자가 오지 않는 종결 부호만 센다.
    sentences = len(re.findall(r"[.!?…](?!\d)", text)) or (1 if text.strip() else 0)
    if sentences > 2:
        failures.append(f"두 문장 초과 ({sentences}문장)")
    if "?" in text or "？" in text:
        failures.append("되묻기(물음표) 포함")
    if re.search(r"(^|\n)\s*([#>*-]|\d+\.)\s", text) or "**" in text:
        failures.append("마크다운/목록 형식 포함")
    return failures


# ---- 2단계: LLM judge (맥락 활용 의미 채점) --------------------------------

JUDGE_PROMPT = """당신은 조리 중 음성 어시스턴트의 답변을 채점하는 심사자입니다.
핵심 채점 포인트는 "어시스턴트가 이전 대화의 맥락을 실제로 활용했는가"입니다.

[레시피] {title} — 원본 재료에 마늘은 없다. 단계:
{steps}

[이전 대화 — 사용자가 레시피에 없는 마늘 5쪽을 편 썰어 추가했다]
{history}

[현재 단계] {step_index}: {instruction}

[사용자 발화]
{user_speech}

[어시스턴트 답변]
{speech_text}

[통과 기준 — 반드시 충족]
{must_convey}

[실격 기준 — 해당하면 실패]
{must_not}

표현이 달라도 의미가 기준을 만족하면 통과입니다.
JSON 하나만 출력: {{"pass": true|false, "reason": "한 문장 근거"}}"""


def judge_grade(context, task, speech_text):
    step = context["recipe"]["steps"][context["current_step_index"]]
    prompt = JUDGE_PROMPT.format(
        title=context["recipe"]["title"],
        steps="\n".join(f"  {s['step_index']}. {s['instruction']}"
                        for s in context["recipe"]["steps"]),
        history="\n".join(f"  {'사용자' if t['role'] == 'user' else '어시스턴트'}: {t['text']}"
                          for t in context["history"]),
        step_index=step["step_index"],
        instruction=step["instruction"],
        user_speech=task["user_speech"],
        speech_text=speech_text,
        must_convey=task["must_convey"],
        must_not=task["must_not"],
    )
    raw = generate(JUDGE_MODEL, [{"role": "user", "parts": [{"text": prompt}]}],
                   temperature=0.0, json_output=True)
    verdict = json.loads(raw)
    return bool(verdict.get("pass")), verdict.get("reason", "")


# ---- 실행 ------------------------------------------------------------------

def run_task(context, task):
    trials = []
    for trial in range(TRIALS):
        record = {"trial": trial, "pass": False}
        try:
            speech = call_target(context, task)
            record["response"] = speech
            code_failures = code_grade(speech)
            record["code_failures"] = code_failures
            if code_failures:
                record["reason"] = "; ".join(code_failures)
            else:
                ok, reason = judge_grade(context, task, speech)
                record["pass"] = ok
                record["reason"] = reason
        except Exception as exception:  # 호출 실패도 transcript에 남긴다
            record["reason"] = f"호출 오류: {exception}"
        trials.append(record)
    passed = sum(1 for t in trials if t["pass"])
    return {"task": task, "trials": trials, "passed_trials": passed,
            "pass": passed * 2 > TRIALS}  # 과반


def main():
    if not API_KEY:
        sys.exit("GEMINI_API_KEY 필요")

    context = json.loads((EVAL_DIR / "conversation.json").read_text())
    tasks = [
        json.loads(line)
        for line in (EVAL_DIR / "dataset.jsonl").read_text().splitlines()
        if line.strip()
    ]
    if TASK_FILTER:
        tasks = [t for t in tasks if TASK_FILTER in t["id"] or TASK_FILTER in t["category"]]
    if not tasks:
        sys.exit(f"필터 '{TASK_FILTER}' 에 해당하는 태스크 없음")

    started = time.time()
    results = []
    for i, task in enumerate(tasks, 1):
        result = run_task(context, task)
        results.append(result)
        mark = "PASS" if result["pass"] else "FAIL"
        print(f"[{i}/{len(tasks)}] {mark} {task['id']} "
              f"({result['passed_trials']}/{TRIALS} trials)")
        for t in result["trials"]:
            status = "ok" if t["pass"] else "NG"
            print(f"    trial {t['trial']} [{status}] {t.get('reason', '')}")
            if t.get("response"):
                print(f"        응답: {t['response']}")

    total_ok = sum(1 for r in results if r["pass"])
    print(f"\n== 요약: {total_ok}/{len(results)} 통과 ({time.time() - started:.0f}s) ==")

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out = EVAL_DIR / "results" / f"{timestamp}.jsonl"
    out.parent.mkdir(exist_ok=True)
    with out.open("w") as f:
        for r in results:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"transcript: {out.relative_to(EVAL_DIR.parent)}")
    sys.exit(0 if total_ok == len(results) else 1)


if __name__ == "__main__":
    main()
