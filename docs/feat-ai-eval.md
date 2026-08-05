# feat/ai-eval — 조리 중 AI 피드백, 대화 맥락 활용 평가(eval)

## 무엇을 왜

조리 중 사용자가 레시피에 **없는** 재료(마늘)를 대화로 추가한 뒤, 그 재료에 대한 후속 발화가
왔을 때 모델이 이전 대화 맥락을 실제로 활용하는지 측정한다. "이전 대화 전체를 컨텍스트로
통째로 주입"하는 방식(RAG 이전의 가장 단순한 안)의 품질을 먼저 확인하는 것이 목적.
구성은 Anthropic 의 [Demystifying evals for AI agents](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents)
를 따른다: 코드 채점 + LLM judge 2단, 시행 반복, transcript 저장.

## 시나리오

계란볶음밥(원본 재료에 마늘 없음). 조리 중 사용자가 대화로 마늘 5쪽을 편 썰어 추가했다
(`eval/conversation.json` 의 스크립트된 대화). 그 맥락 위에서 4개 발화를 던진다:

| id | 발화 | 검증 포인트 |
|---|---|---|
| context-spicy-no-spicy-recipe | 너무 매운데 어떻게 해? | 원본에 매운 재료 없음 → 추가한 마늘로 원인 연결 |
| context-garlic-smell | 마늘향이 너무 많이 나 | 레시피에 없는 재료를 대화 맥락으로 인지 |
| context-galic-synonym-burnt | 갈릭이 너무 많고 탄 거 같은데? | 동의어(갈릭=마늘) 해석 + 탄 재료 대응 |
| context-sliced-thing-burnt | 편으로 썰린 게 까맣게 됐어 | 마늘 언급 없이 지시("편으로 썰린 것")만으로 대상 특정 |

## 핵심 설계 결정과 근거

- **서버를 거치지 않고 Gemini 직접 호출.** 현재 API(`AiFeedbackRequest`)에는 대화 이력
  필드가 없어 서버 블랙박스로는 이 테스트가 불가능하다. 러너가 `CookingCoachClient` 의
  시스템 프롬프트를 복제하고 대화를 멀티턴 `contents` 로 주입한다 — 즉 이것은
  **"이력 주입 파이프라인"의 프롬프트 프로토타입 평가**다. API 에 이력 필드가 생기면
  서버 호출로 되돌린다.
- **프롬프트 복사본 드리프트 주의.** `run_eval.py` 의 `SYSTEM_PROMPT` / `USER_PROMPT_TEMPLATE` 는
  `CookingCoachClient.java` 복사본. 서버 프롬프트 변경 시 같이 바꿔야 한다.
- **레시피는 명시적 주입.** DB 를 쓰지 않고 `conversation.json` 에 계란볶음밥
  (시드 V3 `…0005` 와 동일 내용)을 넣었다. postgres·서버 기동 없이 러너 단독 실행 가능.
- **채점 2단.** (1) 코드 채점: 두 문장 이내, 되묻기(물음표) 금지, 마크다운 금지, 한국어.
  (2) LLM judge(`gemini-3.5-pro`, 대상 flash 와 분리): judge 프롬프트에 대화 전문을 넣어
  "맥락을 실제로 활용했는가"를 `must_convey`/`must_not` 기준으로 의미 채점.
- **태스크당 3시행, 과반 통과.** 비결정성 흡수.
- **JUnit/CI 아님.** 유료·느림·비결정적. 프롬프트/모델 바꿀 때 수동 실행, 결과 커밋으로 회귀 비교.

## 구조

```
eval/
  conversation.json  # 시나리오: 레시피(명시 주입) + 현재 단계 + 스크립트된 대화 이력
  dataset.jsonl      # 태스크 4개 {id, category, user_speech, must_convey, must_not}
  run_eval.py        # 러너 (표준 라이브러리만, 의존성 없음)
  results/           # 실행별 transcript(jsonl). git에 커밋해 회귀 기록으로 쓴다
```

## 실행 방법

```bash
GEMINI_API_KEY=... python3 eval/run_eval.py                 # 전체 (서버·DB 불필요)
GEMINI_API_KEY=... EVAL_FILTER=burnt python3 eval/run_eval.py
EVAL_TARGET_MODEL=... EVAL_TRIALS=5 ...                     # 모델/시행 수 오버라이드
```

종료 코드 0 = 전 태스크 통과. 시행별 사유와 실제 응답이 stdout 에 찍힌다.

## 알려진 약점·후속

- 대화 이력의 어시스턴트 턴은 가상 스크립트다(실제 모델 응답 아님). 모델이 자기 말투와 다른
  이력을 받는 셈이지만, 맥락 활용 측정에는 지장 없다고 판단.
- 원 대화의 마지막 두 사용자 발화("안다져야겠다" / "다음은 뭐해야 해?")는 Gemini 의
  역할 교대 제약 때문에 한 턴으로 합쳤고, 단계 전환 확인용 어시스턴트 턴을 하나 추가했다.
- 후속 실험 후보(이번 범위 아님): 이력 없이 같은 발화를 던져 실패를 확인하는 대조군,
  이력이 매우 길 때(수십 턴) 열화 측정, RAG(관련 턴만 검색 주입) 대비 비교.
- judge 캘리브레이션(사람 채점 대조) 없음. 태스크가 늘면 샘플 대조 필요.
