# 06. CookPilot 기술 스택과 아키텍처

작성 기준: 2026-07-10 확정 MVP

## 1. 기술 목표

MVP의 기술 목표는 완성도 높은 전체 플랫폼이 아니라 다음 루프를 실제로 동작시키는 것이다.

```text
Recipe Runner
-> Active Cooking Voice Mode
-> Command Router
-> Contextual LLM Feedback
-> Post-cook Review
-> Personal Recipe Version
```

## 2. 기술 스택 초안

| 영역 | 초안 |
|---|---|
| Frontend | Flutter |
| Backend | Java / Spring |
| DB | PostgreSQL |
| AI | STT, TTS, LLM |
| 배포 | 추후 확정 |
| 관찰 | 최소 로그 우선, 이후 Sentry/PostHog 검토 |

## 3. 클라이언트 아키텍처

```text
Cooking Screen
  -> Recipe Runner
  -> Local Timer
  -> Voice Listener
  -> TTS Player
  -> Command Router Client
  -> API Client
```

### 책임 분리

| 모듈 | 책임 |
|---|---|
| Cooking Screen | 현재 단계, 타이머, 음성 상태, fallback 버튼 표시 |
| Recipe Runner | 단계 이동, 현재 단계 계산, 자동 다음 안내 |
| Local Timer | 단계별 타이머 시작/연장/정지/완료 |
| Voice Listener | 조리 화면에서만 STT 활성화 |
| TTS Player | 단계 안내와 피드백 읽기 |
| Command Router Client | 명확한 로컬 명령 1차 처리 |
| API Client | 세션/이벤트/LLM/피드백 저장 |

## 4. 백엔드 아키텍처

```text
API Controller
  -> Cook Session Service
  -> Recipe Service
  -> Command Router Service
  -> AI Feedback Service
  -> Personal Recipe Service
  -> PostgreSQL
```

### 주요 서비스

| 서비스 | 책임 |
|---|---|
| Recipe Service | 기본 레시피, 단계, 재료 조회 |
| Cook Session Service | 조리 세션 생성/상태/이벤트 저장 |
| Command Router Service | 서버 측 의도 분류 보정, LLM 호출 여부 결정 |
| AI Feedback Service | 현재 단계 맥락 기반 LLM 응답 생성 |
| Personal Recipe Service | 조리 후 피드백 구조화, 개인 버전 생성 |

## 5. 음성 처리 방식

```text
사용자 발화
-> STT
-> Command Router
   -> 로컬 명령: 앱에서 즉시 처리
   -> 예외 상황: backend LLM 호출
-> 응답 텍스트
-> TTS
-> 세션 이벤트 저장
```

## 6. 로컬 우선 처리

LLM 없이 처리할 명령:

- 다음
- 이전
- 다시 말해줘
- 지금 단계 알려줘
- 1분 더
- 타이머 멈춰
- 타이머 다시 시작

이 명령들은 네트워크 비용과 응답 지연을 줄이기 위해 클라이언트에서 우선 처리한다.

## 7. LLM 호출 조건

LLM은 다음 조건에서만 호출한다.

- 현재 단계 맥락이 필요한 조리 문제
- 재료 생략/대체 판단
- 간 조절 판단
- 안전 관련 질문
- 조리 후 피드백을 개인 레시피 수정안으로 변환할 때

## 8. LLM 입력 구조

```json
{
  "recipeName": "라면",
  "currentStep": {
    "index": 1,
    "instruction": "물 500ml를 넣고 끓인다.",
    "targetTimeSec": 180
  },
  "timer": {
    "remainingSec": 20,
    "elapsedSec": 160
  },
  "userSpeech": "물이 안 끓어",
  "recentEvents": []
}
```

## 9. LLM 응답 구조

```json
{
  "speechText": "아직 끓지 않으면 1분 더 끓이고, 기포가 올라오면 다음 단계로 넘어가세요.",
  "screenText": "1분 더 끓인 뒤 기포가 올라오면 다음 단계로 이동하세요.",
  "suggestedAction": {
    "type": "EXTEND_TIMER",
    "seconds": 60
  },
  "eventPayload": {
    "problem": "water_not_boiling",
    "adjustment": "extend_timer_60s"
  }
}
```

## 10. 비용 제어

| 비용 영역 | 제어 방식 |
|---|---|
| STT | 조리 화면에서만 활성화, 짧은 발화 단위 처리 |
| TTS | 가능하면 기기 내장 TTS 우선 |
| LLM | 예외 상황과 개인화 구조화에만 호출 |
| 타이머 | 로컬 처리 |
| 단계 진행 | 로컬 처리 |
| 단순 명령 | 로컬 처리 |

## 11. 안전 원칙

- 변질 의심은 먹어도 된다고 단정하지 않는다.
- 덜 익은 육류/해산물은 추가 가열을 우선 안내한다.
- 알레르기 질문은 보수적으로 답한다.
- 화기 위험은 즉시 안전 행동을 안내한다.

