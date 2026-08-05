# feat/basic-ai-feedback — 조리 중 AI 피드백에 Spring AI 붙이기

## 1. 무엇을 왜

`POST /api/v1/ai-feedback` 은 지금까지 **고정 목데이터**만 돌려줬다(`AiFeedbackService` 의 하드코딩된
"1분 더 끓이세요"). 이 브랜치는 그 자리에 **Spring AI 기반 실제 LLM 호출**을 넣고,
같이 **응답 계약을 실제로 쓰는 것만 남기고 잘라냈다**.

바꾸지 않은 것:

- **요청 계약** — `{recipeId, stepIndex, userSpeech}` 그대로. `userSpeech` 는 **클라이언트가 STT 로
  변환해 보낸 문자열**이라는 전제를 유지한다. 서버는 음성을 받지 않는다.
- **무상태** — 조리 세션·타이머·이벤트 로그는 계속 프론트가 들고 있다.
- **DB** — 마이그레이션 0건. AI 피드백은 아무것도 저장하지 않는다.

`mock` 플래그의 의미가 바뀌었다: 예전엔 "AI 파트 미확정" 이었고, 지금은 **"이번 응답이 LLM 이 아니라
목데이터인가"** 다. 키가 없거나 호출에 실패하면 `true`.

## 2. 핵심 설계 결정과 근거

### 2-1. Spring AI **2.0.0** — 선택지가 아니라 유일한 답

| | Spring AI 1.1.x | Spring AI 2.0.0 |
|---|---|---|
| 대상 Boot | 3.x | **4.1.0** (이 프로젝트) |
| Jackson | 2 (`com.fasterxml`) | **3 (`tools.jackson`)** |

1.1.x 를 올리면 자동설정부터 깨진다. 2.0.0 의 `spring-ai-starter-model-google-genai` 는 pom 에
`spring-boot 4.1.0` 이 그대로 박혀 있고 Jackson 3 을 쓴다 — 이 저장소와 정확히 같은 라인이다.

Boot BOM 이 spring-ai 버전을 관리하지 않으므로 `platform('org.springframework.ai:spring-ai-bom:2.0.0')`
을 따로 가져온다(springdoc·testcontainers 와 같은 상황).

### 2-2. 프로바이더는 google-genai — 기존 키 재사용

F-11 추천 설명이 이미 Google AI Studio 키(`GEMINI_API_KEY`)를 쓴다. `google-genai` 스타터는 **API 키
한 장**으로 도는 Gemini Developer API 다(`vertex-ai` 는 GCP project-id/location 자격증명이 필요).
같은 키를 공유하므로 새 비밀이 늘지 않는다.

**모델 교체 자유도의 실제 범위**

- 같은 프로바이더 내 모델 교체(`gemini-3.5-flash` → `pro`): `AI_CHAT_MODEL` env 하나. 재빌드 없음.
- 프로바이더 교체(Gemini → OpenAI/Claude): **스타터 의존성 교체 + 재빌드**. 프로퍼티 prefix 도
  키도 다르다. 다만 **호출 코드(`ChatClient`)는 그대로** — 이게 Spring AI 를 쓰는 실익이다.
  손코딩한 `GeminiApi` 와이어 DTO 였다면 통째로 다시 썼어야 한다.

### 2-3. 기본값은 `spring.ai.model.chat=none` — 안 그러면 기동이 죽는다

`GoogleGenAiChatAutoConfiguration` 의 조건이 `@ConditionalOnProperty(name="spring.ai.model.chat",
havingValue="google-genai", matchIfMissing=true)` 다. **값을 안 주면 켜진다.** 그리고 키가 없으면
빈 생성에서 `Incomplete Google GenAI configuration: Provide 'api-key' ...` 로 컨텍스트가 죽는다.

CI·로컬·모든 테스트는 키가 없다. 그래서 `application.yml` 기본값을 `${AI_CHAT_PROVIDER:none}` 으로
못 박았다. **켤 때만** `AI_CHAT_PROVIDER=google-genai`.

### 2-4. `ChatClient.Builder` 가 아니라 `ChatModel` 을 주입받는다

처음엔 `ObjectProvider<ChatClient.Builder>` 로 짰는데 기동이 깨졌다:

```
Error creating bean 'chatClientBuilder' ... : No qualifying bean of type
'org.springframework.ai.chat.model.ChatModel' available
```

`ChatClientAutoConfiguration` 은 ChatModel 유무와 무관하게 `chatClientBuilder` **빈 정의를 등록**한다.
그래서 모델이 없을 때 `getIfAvailable()` 이 `null` 이 아니라 예외로 터진다. `ChatModel` 은 provider 가
`none` 이면 정의 자체가 없어서 깨끗하게 `null` 이 나온다. 그래서 `ObjectProvider<ChatModel>` 로 받고
`ChatClient.builder(model)` 로 직접 만든다.

### 2-5. 타임아웃은 직접 걸어야 한다 — SDK 기본값이 **무한대**

google-genai SDK 는 타임아웃을 끄고 시작한다:

```java
// google-genai 1.58.0 ApiClient.java
// Remove timeouts by default (OkHttp has a default of 10 seconds)
builder.connectTimeout(Duration.ofMillis(0));
builder.readTimeout(Duration.ofMillis(0));
builder.writeTimeout(Duration.ofMillis(0));
httpOptions.timeout().ifPresent(t -> builder.callTimeout(...));  // 안 주면 무한
```

그리고 Spring AI 의 `spring.ai.google.genai.*` 에는 **타임아웃 프로퍼티가 없다**(설정 메타데이터 전수 확인).
그대로 두면 Gemini 가 응답하지 않을 때 서블릿 스레드가 영구 점유돼 톰캣 스레드풀이 마른다.

Spring AI 의 `googleGenAiClient` 빈이 `@ConditionalOnMissingBean` 이라, `GoogleGenAiClientConfig` 에서
같은 타입을 먼저 등록해 자동설정을 물러나게 하고 `HttpOptions.timeout(10_000)` 을 건다.

타임아웃 초과 예외는 `GenAiIOException`(→ `BaseException extends RuntimeException`)이라
기존 `catch (RuntimeException)` 이 그대로 잡아 목 응답으로 흡수한다.

이 빈이 자동설정에 밀리면 **타임아웃만 조용히 사라지고 호출은 계속 된다** — 운영에서 스레드가 마를 때까지
모른다. 그래서 `AiFeedbackWiringTest` 가 "이 Client 를 누가 만들었는가"를 빈 정의로 직접 단언한다.

### 2-6. 실패는 전부 목 응답으로 흡수

F-11 `GeminiRecommendationExplanationClient` 와 같은 패턴이다. 조리 중에 500 을 던지면 사용자는
불 앞에서 아무 답도 못 듣는다. 다음 셋 중 무엇이 일어나도 `mock=true` 응답이 나간다:

1. 설정이 꺼져 있음(ChatModel 빈 없음)
2. 호출 실패(네트워크·쿼터·타임아웃)
3. 빈 응답

### 2-7. 응답은 문장 하나 — 구조화 출력을 버렸다

처음에는 `CookingAdvice` 레코드로 구조화 출력을 받았다(`speechText`/`screenText`/`actionType`/
`seconds`/`problem`/`adjustment`). 이 필드들은 옛 MVP 문서의 LLM 응답 구조를 그대로 옮긴 것이었고,
`AiFeedbackResponse` 도 main 부터 같은 모양이었다.

전부 뺐다. 이유는 각각 다르다:

| 뺀 필드 | 이유 |
|---|---|
| `suggestedAction{type, seconds}` | 프론트가 앞단에서 처리하기로 했다. 서버가 행동 어휘를 정의하면 그게 곧 클라이언트 계약이 되는데, 지금 그럴 근거가 없다 |
| `eventPayload{problem, adjustment}` | 분석용 영문 라벨. **서버도 저장하지 않고 프론트에도 소비 코드가 없다** — 순수 데드웨이트였다 |
| `screenText` | 화면 표시 문구도 프론트가 정한다 |

남은 것은 `{mock, speechText}` 둘이다. 필드가 하나면 구조화 출력을 쓸 이유가 없어
`ChatClient…entity(CookingAdvice.class)` 를 `.content()` 로 바꿨고, 그러면서 출력 검증
(`normalize()` 21줄 + 상수 3개 + 테스트 5개)이 통째로 사라졌다.

**남은 검증은 "빈 문자열이면 버린다" 하나뿐이다.** 형식은 프롬프트에 맡긴다 — 읽히는 것이 문장
하나뿐이라 모델이 형식을 어겨도 폭발 반경이 없다.

필요해지면 그때 필드를 늘린다. 늘리는 쪽이 줄이는 쪽보다 싸다.

### 2-8. 프롬프트에 박아 둔 것

- **안전 원칙 4줄** — 변질 의심 단정 금지, 덜 익은 육류는 추가 가열 우선, 알레르기 보수적,
  화기 위험 최우선. 안전 문구는 모델 재량에 맡기지 않는다.
- **되묻기 금지.** STT 결과는 오인식·잡음·끊긴 문장이 섞인다. 조리 중에 되묻는 것은 사용자에게
  가장 비싼 응답이라, 발화가 불분명하면 현재 단계 기준 가장 안전한 안내를 하게 했다.
- **머리말·목록·마크다운 금지.** 응답이 그대로 TTS 로 읽히기 때문이다.

## 3. 스키마 / API 변경

- **DB 스키마 변경 없음.** 마이그레이션 추가 없음.
- **요청 변경 없음.** `{recipeId, stepIndex, userSpeech}`.
- **응답 필드 제거(파괴적).**

```jsonc
// before
{ "mock": true, "speechText": "...", "screenText": "...",
  "suggestedAction": { "type": "EXTEND_TIMER", "seconds": 60 },
  "eventPayload": { "problem": "...", "note": "...", "currentStepIndex": 0, "userSpeech": "..." } }

// after
{ "mock": false, "speechText": "..." }
```

`docs/openapi.json` 재생성 완료(`SuggestedAction` 스키마 제거). CI 가 코드와 스냅샷 일치를 검사하므로
이 파일이 밀리면 파이프라인이 막힌다.

**설정 변경**

| env | 기본값 | 뜻 |
|---|---|---|
| `AI_CHAT_PROVIDER` | `none` | `google-genai` 로 바꾸면 LLM 을 실제로 호출 |
| `AI_CHAT_MODEL` | `gemini-3.5-flash` | 같은 프로바이더 내 모델 교체 |
| `GEMINI_API_KEY` | (비어 있음) | F-11 과 공유 |

`docker-compose.prod.yml` 의 app 컨테이너에 위 3개를 전달하도록 추가했다(기존엔 GEMINI_* 가 앱으로
아예 전달되지 않고 있었다 — F-11 도 사실상 꺼진 상태였다).

## 4. 변경 파일

| 파일 | 변경 |
|---|---|
| `build.gradle` | spring-ai-bom 2.0.0 + `spring-ai-starter-model-google-genai` |
| `application.yml` | `spring.ai.model.chat` 기본 none, google-genai 키/모델/temperature |
| `ai/CookingCoachClient.java` | **신규** — ChatClient 호출, 시스템 프롬프트, 빈 응답 처리 |
| `ai/GoogleGenAiClientConfig.java` | **신규** — 호출 타임아웃 10초(SDK 기본값이 무한대라 직접 건다) |
| `ai/AiFeedbackRequest.java` | **신규** — 컨트롤러 중첩 레코드였던 요청 DTO 를 파일로 분리 + STT 전제 주석 |
| `ai/AiFeedbackResponse.java` | `screenText`·`suggestedAction`·`eventPayload` 제거 → `{mock, speechText}` |
| `ai/AiFeedbackService.java` | LLM 경로 추가, 실패 시 목 문장으로 fallback |
| `ai/AiFeedbackController.java` | 중첩 DTO 제거 |
| `.env.example`, `docker-compose.prod.yml` | AI env 3개 배선 |
| `docs/openapi.json` | 재생성 |
| `ai/CookingCoachClientTest.java` | **신규** — AI 가 꺼져 있으면 호출하지 않는지 |
| `ai/AiFeedbackWiringTest.java` | **신규** — AI 를 켠 컨텍스트가 뜨는지 + Client 빈 소유자 단언 |
| `ai/AiFeedbackApiTest.java` | 제거된 필드 단언 삭제 |

## 5. 알려진 약점 · 후속

1. **실제 호출 경로는 자동 테스트가 없다.** `AiFeedbackWiringTest` 는 더미 키로 배선만 본다.
   프롬프트가 실제로 쓸 만한 답을 내는지는 키를 꽂고 수동으로 확인해야 한다.
2. **AI 호출 경로가 2개다.** F-11 은 손코딩 `RestClient`(`GeminiRecommendationExplanationClient`),
   이번 건은 Spring AI. 설정도 `cookpilot.ai.gemini.*` 와 `spring.ai.*` 로 이원화돼 있다.
   의도적으로 이번 브랜치 범위에서 뺐다 — F-11 을 Spring AI 로 이관하는 것은 별도 PR.
3. **`userSpeech` 길이 상한이 없다.** 무인증 엔드포인트라 큰 텍스트를 계속 밀어 넣으면 쿼터를 태울 수 있다.
   프론트가 STT 결과 길이를 제한하는 전제로 이번 범위에서 뺐다 — 서버로 넘어오면 컨트롤러에 상한을 건다.
4. **비용·쿼터 제어 없음.** 레이트리밋도 캐시도 없다. 무료 티어 쿼터를 넘기면 매 호출이 실패해
   전부 목으로 떨어진다(사용자에게는 조용히). 베타 규모에서는 감수, 이후 관측 지표 필요.
5. **응답 형식 보장이 프롬프트뿐이다.** 모델이 머리말이나 목록을 붙이면 그대로 TTS 로 읽힌다.
   구조화 출력을 버린 대가다 — 실제로 얼마나 자주 어기는지는 운영에서 봐야 한다.
6. **조리 상태를 안 넘긴다.** 타이머 잔여시간·최근 이벤트가 프롬프트에 없다.
   "3분 중 20초 남음" 을 모르는 상태로 조언하는 셈이다. 요청 DTO 확장이 선행돼야 한다.
7. **`mock` 플래그 의미 변경.** 프론트가 `mock:true` 를 "개발 중" 배지로 쓰고 있었다면
   이제는 "이번 답은 LLM 이 아님" 으로 읽어야 한다.
