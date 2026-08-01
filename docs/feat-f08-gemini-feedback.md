# F-08 Gemini 조리 중 예외 피드백

## 범위

조리 세션·단계 이동·타이머 상태는 계속 프론트가 관리한다. F-08 백엔드는 현재
실행 맥락을 받아 짧은 안내를 생성할 뿐이며 DB나 조리 세션 테이블을 추가하지 않는다.

처리 순서는 다음과 같다.

1. `X-CookPilot-User-Id`로 폐쇄 베타 사용자를 확인한다.
2. `recipeId`가 실제 레시피인지 확인한다.
3. 화재·알레르기·부패·덜 익은 육류 등은 서버 안전 규칙으로 즉시 답한다.
4. 그 외 요청은 사용자별 분당 한도를 확인한 뒤 Gemini를 호출한다.
5. Gemini 호출 또는 응답 검증이 실패하면 보수적인 서버 fallback을 반환한다.

안전 규칙은 Gemini 호출 한도가 소진되어도 계속 응답한다.

## API 계약

기존 엔드포인트와 필드를 유지한다.

```http
POST /api/v1/ai-feedback
X-CookPilot-User-Id: <beta-user-uuid>
Content-Type: application/json
```

```json
{
  "recipeId": "10000000-0000-0000-0000-000000000001",
  "stepIndex": 2,
  "userSpeech": "물이 안 끓어요",
  "instruction": "뚜껑을 덮고 물을 끓인다.",
  "remainingSeconds": 20
}
```

- `recipeId`, `stepIndex`, `userSpeech`: 기존 필수 필드
- `instruction`: 선택 필드, 최대 1,000자
- `remainingSeconds`: 선택 필드, `0..86400`
- `userSpeech`: 최대 500자

개인 레시피 버전은 단계를 추가·제거한 뒤 실행 순서로 다시 인덱싱할 수 있다.
따라서 `instruction`이 있으면 프론트 실행 스냅샷을 현재 단계의 정본으로 사용하고
원본 레시피의 같은 `stepIndex`를 강제하지 않는다. 구버전 요청처럼 `instruction`이
없을 때만 원본 단계 설명으로 보완하며, 이 경우 없는 단계는 404다.

응답 형태도 기존 계약을 유지한다.

```json
{
  "mock": false,
  "speechText": "화력을 한 단계 높이고 1분 더 기다려보세요.",
  "screenText": "원하면 타이머를 1분 연장할 수 있어요.",
  "suggestedAction": {
    "type": "EXTEND_TIMER",
    "seconds": 60
  },
  "eventPayload": {
    "problem": "WATER_NOT_BOILING",
    "source": "GEMINI",
    "currentStepIndex": 2
  }
}
```

`suggestedAction`은 없을 수 있다. 허용 값은 `EXTEND_TIMER`와 30초 또는 60초뿐이며,
서버는 실제 타이머를 변경하지 않는다. 프론트가 사용자 승인을 받은 뒤 적용한다.

`eventPayload.source`는 `SAFETY_RULE`, `GEMINI`, `FALLBACK` 중 하나다. 원문
`userSpeech`는 event payload, DB, 서버 로그에 남기지 않는다.

## Gemini 신뢰 경계

- F-08 생성 경로는 Spring AI 2.0의 `ChatClient`와 `GoogleGenAiChatModel`을
  사용한다. 기존 추천 설명 생성 경로는 이번 변경 범위에 포함하지 않는다.
- 키는 Google GenAI SDK의 `apiKey` 설정으로만 전달하고 URL·프롬프트·프론트·
  로그에 넣지 않는다.
- 고정 안전 정책은 Gemini `systemInstruction`에 두고, 실행 문맥과 사용자 발화는
  별도의 `role=user` JSON 데이터로 보낸다.
- Spring AI provider-native structured output으로 `AiFeedbackModelOutput` 스키마를
  Gemini에 전달한다. 그 뒤에도 `SafetyAdvisor`가 정확한 필드 집합, 문구 길이,
  문제 코드, 행동 종류와 초를 다시 검증한다.
- 모델 문구가 다음 단계 이동이나 조리 완료를 지시·선언하면 응답 전체를 버리고
  결정적인 서버 fallback을 사용한다. 완료 여부를 묻거나 이동을 금지하는 문구는
  허용한다.
- Gemini가 화재·알레르기·변질·덜 익음·탐 위험으로 분류하면 모델 문구와 행동을
  폐기하고 서버가 소유한 보수적 안전 문구로 교체한다.
- `SafetyAdvisor`는 호출 전 서버 안전 규칙과 호출 후 응답 검증을 담당하고,
  `FallbackAdvisor`는 전송·모델·구조화 변환 실패를 fallback 신호로 바꾼다.
- tool calling과 Google Search를 등록하지 않는다. SDK와 Spring AI 재시도뿐 아니라
  transport의 연결 재시도·redirect·즉시 503 follow-up도 끈다. HTTP/2 421 follow-up을
  피하려고 F-08 transport는 HTTP/1.1만 사용하므로, 한도 검사를 통과한 요청 하나는
  외부 모델 HTTP exchange를 최대 한 번 만든다.
- `GEMINI_MODEL`을 2.5 계열로 바꾸면 `thinkingBudget`을 사용하고, 3 계열은
  `thinkingLevel=low`를 사용한다. thinking을 끌 수 없는 2.5 Pro에는 공식
  최소 예산 128을 적용한다.
- 잘못된 JSON, 빈 후보, timeout, 429, 5xx는 모두 fallback으로 흡수한다.
- 화재·알레르기 등 안전 답변은 Gemini보다 먼저 결정한다.
- 알레르기와 덜 익음 발화는 절 순서대로 상태를 갱신한다. 과거 사건·부정·현재
  해소는 위험으로 남기지 않되, 해소 뒤 `다시`/`또` 나타난 증상이나 다시 보고된
  덜 익음은 현재 위험으로 복구한다. 마침표·콜론·세미콜론·줄바꿈도 같은 절
  경계로 본다.

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `GEMINI_ENABLED` | `false` | Gemini 호출 활성화 |
| `GEMINI_API_KEY` | 없음 | Google AI Studio 서버 키 |
| `GEMINI_MODEL` | `gemini-3.5-flash` | 호출 모델 |
| `GEMINI_CONNECT_TIMEOUT` | `2s` | 연결 제한 시간 |
| `GEMINI_READ_TIMEOUT` | `4s` | F-08 모델 요청 전체 제한 시간 |
| `AI_FEEDBACK_REQUESTS_PER_MINUTE` | `20` | 사용자별 F-08 분당 요청 수 |

`GEMINI_CONNECT_TIMEOUT`은 같은 설정을 공유하는 기존 추천 설명 생성 경로에서
계속 사용한다. Google GenAI SDK 기반 F-08 경로는 `GEMINI_READ_TIMEOUT`을 HTTP
요청 전체 제한 시간으로 적용한다.

현재 rate limiter는 단일 서버 메모리 기준이다. 서버가 여러 인스턴스로 확장되면
Redis 등 공유 저장소 기반 제한으로 교체해야 한다.

Google의 무료(unpaid) Gemini 서비스에서는 프롬프트와 응답이 제품 개선이나 사람의
검토에 사용될 수 있다. 데모에서는 개인 식별 정보와 민감한 의료·알레르기 정보를
Gemini로 보내지 말고, 운영 전 사용자 안내·동의 및 유료 티어 사용 여부를 검토한다.
