# fix/genai-client-api-key — Gemini API 키가 Client 에 안 박히던 버그

## 무엇을 왜

조리 중 AI 피드백(`POST /api/v1/ai-feedback`)이 **프로덕션에서 한 번도 동작한 적이 없다**. 응답은
항상 `{"mock": true, ...}` 였다. `GoogleGenAiClientConfig` 가 만드는 `com.google.genai.Client` 의
apiKey 에 실제 키가 아니라 **리터럴 문자열 `${spring.ai.google.genai.api-key}`** 가 박혀 있었고,
그게 그대로 `x-goog-api-key` 헤더로 나가 Gemini 가 400(API_KEY_INVALID)을 돌려줬다. 그 예외를
`CookingCoachClient.advise` 가 목 응답으로 흡수해서 겉으로는 아무 일도 없어 보였다.

`GoogleGenAiClientConfig` 는 `29c0c99`(#41) 단일 커밋에서 도입됐고, 그 커밋부터 계속 이 상태였다.
F-11 추천 설명 경로는 무관하다 — 별도 `RestClient` + `@ConfigurationProperties` 라 정상이다.

## 원인 (왜 플레이스홀더가 안 풀렸나)

`@Value` 가 고장 난 게 아니다. **빈이 만들어지는 시점이 너무 이르다.**

1. spring-ai 2.0.0 의 `CachedContentServiceCondition.getMatchOutcome` 이 조건 평가 도중
   `context.getBeanFactory().getBean(GoogleGenAiChatModel.class)` 를 호출한다.
2. 그 조건이 붙은 `googleGenAiCachedContentService` 는 `enable-cached-content` 가
   `matchIfMissing = true` 라 기본으로 켜져 있다 → 이 경로가 항상 밟힌다.
3. 조건 평가는 `ConfigurationClassPostProcessor`(BeanDefinitionRegistryPostProcessor) 단계라
   **일반 BFPP 보다 앞선다**. `PropertySourcesPlaceholderConfigurer` 가 바로 그 일반 BFPP다.
4. 그래서 `googleGenAiClient` → `googleGenAiChatModel` 이 PSPC 보다 먼저 만들어지고, 그 시점
   `resolveEmbeddedValue` 는 등록된 리졸버가 없어 **예외를 던지지 않고 입력 문자열을 그대로 반환**한다.
5. 오염된 `Client` 가 싱글턴 캐시에 박혀 JVM 수명 내내 유지된다.

### 왜 아무도 몰랐나 — 침묵의 3중 구조

| 지점 | 삼킨 것 |
|---|---|
| `AbstractBeanFactory.resolveEmbeddedValue` | 리졸버가 없으면 throw 없이 원문 반환 |
| `CachedContentServiceCondition` | 본문 전체가 `catch (Exception) → noMatch` |
| `CookingCoachClient.advise` | `exception.getClass().getSimpleName()` 만 로깅 → prod 로그에 `(RuntimeException)` 만 |

여기에 자동설정이 갖고 있던 fail-fast(`"Incomplete Google GenAI configuration"` 으로 기동 중단)를
우리 빈이 `@ConditionalOnMissingBean` 을 이기면서 통째로 없애버린 것이 겹쳤다.

## 근거 (재현 실험)

배포 코드 그대로 컨텍스트를 띄우고 `Client#apiKey()` 를 읽었다.

| 실험 | 결과 |
|---|---|
| 현상 재현 | `clientKey=[${spring.ai.google.genai.api-key}]` |
| 대조군 — 같은 컨텍스트의 **다른** `@Bean` 에 같은 `@Value` | `[PROBE-KEY-12345]` — `@Value` 는 멀쩡 |
| 일반 BFPP 단계 진입 시 싱글턴 존재 여부 | `clientAlreadyInstantiatedBeforeRegularBfppPhase=true` |
| 트리거 제거 (`spring.ai.google.genai.chat.enable-cached-content=false`) | 조기 생성 사라지고 `clientKey=[PROBE-KEY-12345]` |

마지막 줄이 원인→결과 링크를 확정한다.

## 무엇을 고쳤나

| 파일 | 변경 |
|---|---|
| `GoogleGenAiClientConfig` | `@Value` 파라미터 → `Environment` 주입 후 `getProperty`. `Environment` 는 `refresh()` 첫 순간부터 완성돼 있어 빈 생성 순서를 타지 않는다 |
| `GoogleGenAiClientConfig` | 키가 비었거나 `${` 로 시작하면 `IllegalStateException` 으로 **기동 중단**. 자동설정에서 잃어버린 fail-fast 를 되돌린다 |
| `AiFeedbackWiringTest` | `Client#apiKey()` 가 **치환된 값**인지 확인하는 테스트 추가 |
| `CookingCoachClient` | 실패 로그에 예외 타입명만이 아니라 스택을 남긴다 |

`enable-cached-content=false` 로도 증상은 사라지지만 채택하지 않았다 — 남의 자동설정 조건 평가
순서에 기대는 회피책이고, spring-ai 버전이 바뀌면 조용히 되돌아온다.

`GoogleGenAiClientConfig` 삭제(= 자동설정에 맡기기)도 검토했다가 **버렸다.** 실측 결과
`httpOptions.timeout=Optional.empty` 가 되어 SDK 기본값인 무한 타임아웃이 돌아온다 — 목 응답
버그를 톰캣 스레드 고갈 버그와 맞바꾸는 셈이다.

## 검증

- `AiFeedbackWiringTest.Gemini_Client에_해석된_키가_박혀_있다` 는 수정을 되돌리면 실패한다
  (`expected: "dummy-key-for-wiring-test" but was: "${spring.ai.google.genai.api-key}"`).
- 키를 비우고 `spring.ai.model.chat=google-genai` 로 띄우면 기동이 죽는다:
  `IllegalStateException: Gemini API 키가 없습니다: ...`
- `./gradlew test` 전체 통과. 스키마/API 계약 변화 없음 → `docs/openapi.json` 변경 없음.

## 알려진 약점·후속

- 기존 `Gemini_Client는_타임아웃을_건_우리_빈이다` 는 `getFactoryBeanName()` 만 봐서 이 버그를
  통과시켰다. "우리 빈이 이겼다"만 검증하고 "이긴 빈이 쓸모있나"는 검증하지 않았던 것.
- `spring.ai.google.genai.api-key: ${GEMINI_API_KEY:}` 의 기본값이 빈 문자열인 건 그대로 뒀다.
  이제는 그 경우 기동에서 죽으므로 조용한 실패가 아니다.
- 실제 Gemini 호출이 성공하는지는 여전히 자동 테스트로 확인하지 않는다(네트워크·과금). 배포 후
  `mock:false` 를 한 번 눈으로 확인해야 한다.
