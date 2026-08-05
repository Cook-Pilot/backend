# 작업 로그 — 백엔드 조리 중 AI 음성처리 (2026-08-05)

- 담당: 이현우 (백엔드 조리 중 AI 음성처리 파트)
- 작업 저장소: `Cook-Pilot/backend`, 로컬 기준 브랜치 `feat/usable-mvp`
- 결과 브랜치: `feat/be-llm-first-coach` (원격 푸시 완료, **PR 미개설**)
- 상세 근거: [`docs/voice-rule-coach-decision-log-2026-08-05.md`](./docs/voice-rule-coach-decision-log-2026-08-05.md),
  [`docs/llm-first-coach-decision-log-2026-08-05.md`](./docs/llm-first-coach-decision-log-2026-08-05.md)

## 0. 개발 환경 — JDK 설치

작업 시작 시 `./mvnw test`가 `Unable to locate a Java Runtime`으로 실패했다. 시스템 JDK, Homebrew,
SDKMAN, IntelliJ가 모두 없는 맥이었다. 백엔드 코드를 검증 없이 넘기지 않으려면 JDK가 먼저였다.

**설치**: Eclipse Temurin 21.0.12+8 (aarch64), `~/Library/Java/JavaVirtualMachines/jdk-21.0.12+8/`

`.pkg` 설치 대신 tar.gz를 홈 디렉터리에 푼 이유는 관리자 비밀번호가 필요 없기 때문이다.
`/usr/libexec/java_home`이 표준 사용자 위치를 인식하므로 셸 프로필이나 `/usr/bin/java`를 건드리지
않았다. 대신 빌드 때마다 `JAVA_HOME`을 인라인으로 지정해야 한다.

```
JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jdk-21.0.12+8/Contents/Home" ./mvnw test
```

베이스라인 테스트 9개 통과 확인 후 작업 시작.

## 1. 격차 분석

`CoachService` → `SafetyRuleCoach` / `GeminiCoach` 경로를 읽고 4건을 확인했다.

| # | 내용 | 이번에 처리 |
|---|---|---|
| 1 | `SafetyRuleCoach`의 한 글자 substring 오탐·누락 | ✅ 수정 |
| 2 | `/ai/feedback`만 `Idempotency-Key`를 쓰지 않음 | ❌ 남김 |
| 3 | 서버측 AI 호출 가드 없음 (프론트 `voice_call_guard`에만 존재) | ❌ 남김 |
| 4 | `GeminiCoach`가 모든 예외를 삼켜 fallback 원인 구분 불가 | ❌ 남김 |

## 2. 룰베이스 오탐 수정 — `e32ea68`

**원인**: 2026-07-30 프론트 커밋에서 `LocalCoach`만 정교화하고 같은 역할의 백엔드를 함께 고치지 않아
두 구현이 갈라졌다.

**증상**: 한국어는 교착어라 한 글자가 다른 단어의 조각으로 들어간다. 두 방향으로 모두 틀렸다.

- 과잉 매칭 — "파스타 면 얼마나 삶아"가 `타`에 걸려 "불을 즉시 줄이고 바닥을 긁지 마세요",
  "진짜 이거 맞아"가 `짜`에 걸려 "물을 한 스푼씩 추가하세요"
- 누락 — `짜`/`짰`만 있고 `짠`이 없어 "소스가 너무 짠 것 같아"가 빠지고, `타`만 있고 `탄`/`탔`이
  없어 "탄내가 나", "고기가 다 탔어"가 전부 빠졌다

**수정**: 짠맛은 프론트 규칙을 이식(맥락어 AND 게이트 + `진`/`가` 선행 배제). 탄 것은 조각으로 나타날 수
없는 2음절 이상 형태만 사용(`눌어붙`, `탄내`, `타는냄새`, `타버`, `타고있`, `탔`, `까맣게`).

**기각한 대안**: 프론트 규칙 그대로 이식(프론트도 `탔어`를 못 잡아 누락이 남음) / `타는` 추가
("파스타는데"로 재충돌) / 선행 음절 배제를 `타`에도 적용(`타이머`는 선행 음절이 없어 분리 불가) /
형태소 분석기 도입(최후 fallback 경로에 의존성·지연 추가는 과함).

**검증**: 고정 말뭉치 회귀 테스트 2개 → 25개. **수정 전 코드로 돌려 13개 실패**를 확인해 회귀
테스트로서 유효함을 입증했다.

## 3. 방향 전환 — 노션 회의록 확인

작업 중 노션 "조리 중 리얼타임 음성처리"(2026-08-04, 최종 편집자 전동훈)를 확인했다. MCP로는 접근
권한이 없어(다른 워크스페이스) export로 받아 읽었다.

회의록 4번 항목이 진행 방향과 충돌했다.

> 룰베이스로 다 커버 가능? → 안됨 → 싹다 API 던져잇!
> 일단 llm 다 던지기로 구현하고 수정해나가야 함. 지금 너무 밀림.
> 알레르기 같은 건 위험하니까 llm 던지지 말자~ → **실제로 llm이 대답 잘 못함?**

**판단**: 2번 작업을 되돌릴 필요는 없다. 고친 것은 `fallbackAnswer` — Gemini 실패 시의 최후 응답이라
"싹다 API 던지기"로 가도 필요하고, LLM 의존도가 올라갈수록 오히려 더 중요해진다. 다만 룰베이스
**커버리지를 넓히는** 방향은 중단하고 LLM-first로 전환했다.

**회의록에서 사실과 다른 부분**: "현 룰베이스 구조 — 단어, 어간, 어미 등을 분석"이라고 적혀 있으나
실제 코드는 형태소 분석을 전혀 하지 않는 단순 substring `contains`다. 팀이 실제보다 정교한 구현을
전제하고 판단하고 있을 수 있다. (결과적으로 "룰베이스로는 안 됨"이라는 회의 결론을 뒷받침한다.)

## 4. LLM-first 전환 — `9f5a6c1`

판단이 필요한 질문은 안전 질문을 포함해 전부 Gemini로 보낸다. 룰베이스는 **삭제하지 않고** 두 자리에
남겼다.

1. LLM 앞 안전 인터셉트 — `cookpilot.ai.safety-intercept-enabled` (기본 `false`).
   `COOKPILOT_SAFETY_INTERCEPT=true`로 즉시 복원.
2. LLM 뒤 fallback — 플래그와 무관하게 항상 동작.

**보존 방식으로 기각한 대안**: 코드 삭제 후 git 이력 의존(비교 실험이 번거로움 — 회의가 요구한 것이
정확히 두 동작의 비교다) / `legacy/` 디렉터리 복사(죽은 코드) / 별도 브랜치 보존(main 계열에서 실험 불가).

복원 기준점으로 태그 `rulebase-coach-v1`을 `e32ea68`에 달았다. (로컬 전용, 원격 미푸시)

**함께 바꾼 것**

- Gemini 프롬프트에 안전 지침 구체화. 안전 질문의 1차 응답자가 LLM으로 넘어갔으므로 룰베이스가 갖고
  있던 내용을 프롬프트로 옮겼다. 특히 기름불에 물을 붓지 말라는 지시는 틀리면 사람이 다친다.
- `ai_interactions.model`에 경로 4종을 구분 기록(`local-safety-intercept` / 모델명 /
  `local-safety-fallback` / `local-rule-fallback`). 이전에는 룰베이스 응답이 전부
  `local-safety-rules` 하나로 뭉뚱그려져 사후 비교 측정이 불가능했다.

**검증**: `CoachServiceTest` 5개 + `CoachServiceConfigBindingTest` 1개. 후자는 유닛 테스트가 생성자를
직접 호출해 프로퍼티 키 오타를 못 잡기 때문에 따로 두었다. 키를 일부러 틀리게 바꿔 이 테스트가 실패하는
것까지 확인했다.

## 5. 로컬 명령 경계 — `26543c5`

회의록 4-1의 단서("단발성 명령은 룰베이스로 — 타이머 추가, 다음 레시피 뭔데")는 **이미 지켜지고 있어
바꾼 것이 없다.** 프론트 `VoiceRoute.localCommand`는 `_runCommand`로 바로 실행되고 `/ai/feedback`을
호출하지 않는다. 타이머 연장·시작·정지·재개, 단계 이동, 반복, 조리 완료가 모두 여기 해당한다.

인접 항목인 LLM의 `suggestedAction: EXTEND_TIMER`는 **현행 유지**로 결정했다. 금지안은, 막으면
"1분 더 익히세요" 조언을 들은 사용자가 타이머를 늘리려 "1분 더"를 다시 발화해야 해서 조리 중 발화가 한 번
더 늘어난다는 이유로 기각했다. 젖은 손과 조작 부담을 줄이는 것이 제품 전제다. 실제 적용은 사용자 확인을
거치고, 왕복 중 단계가 바뀌면 프론트 `revisionToken`이 만료되어 타이머 액션만 버려진다.

## 6. 작업 중 발생한 문제

**스테일 클래스로 인한 오검출.** 프로퍼티 키 오타 실험 후 `mv`로 파일을 복원했는데, `mv`가 원본 mtime을
보존하는 바람에 복원된 소스가 오타 버전으로 컴파일된 `.class`보다 오래된 것으로 보였다. Maven 증분
컴파일이 재컴파일을 건너뛰어 소스는 정상인데 오타 버전이 실행됐다. mtime 비교로 확인하고 클린 빌드로
해소했다. 코드 결함이 아니었다.

교훈: 파일을 되돌릴 때 `mv`(mtime 보존) 대신 `touch`를 함께 하거나 `clean`을 건다.

## 7. 최종 상태

| 항목 | 결과 |
|---|---|
| 테스트 | 9개 → **38개, 전부 통과** (클린 빌드 기준) |
| 커밋 | `e32ea68`, `9f5a6c1`, `26543c5` |
| 원격 | `feat/be-llm-first-coach` 푸시 완료, **PR 미개설** |
| 로컬 전용 | 브랜치 `feat/be-voice-rule-coach-precision`, 태그 `rulebase-coach-v1` |

## 8. 푸시 중 발견 — 로컬과 팀 main이 별개 구현

PR을 열지 않은 이유다.

분기점이 **`ff1465a`(저장소의 두 번째 커밋)**이고, 그 이후 `origin/main`에 팀 커밋 18개가 쌓였다.

| | 로컬 `feat/usable-mvp` | `origin/main` |
|---|---|---|
| 패키지 | `com.cookpilot.api.*` 단일 | `com.cookpilot.backend.*` 기능별 |
| Java 파일 | 16개 | 83개 |
| 데이터 접근 | `JdbcTemplate` + Flyway | JPA |
| AI 피드백 | `CoachService`/`GeminiCoach`/`SafetyRuleCoach` | `ai/AiFeedbackService` |

`origin/main`에는 `SafetyRuleCoach`, `CoachService`, `GeminiCoach`가 **존재하지 않는다.**
`feat/usable-mvp`는 원격에도 없는 로컬 전용 브랜치다.

PR을 열면 오늘 커밋 3개뿐 아니라 `d754ee3`(백엔드 src 전체)이 함께 올라가 "백엔드 전체를 다른
아키텍처로 갈아엎는 PR"로 보인다. 그래서 브랜치만 푸시하고 PR은 보류했다.

**다만 충돌이 아니라 빈칸이다.** `origin/main`의 `AiFeedbackService`는 55줄 목데이터 스텁이고 주석에
이렇게 적혀 있다.

> AI 파트 미확정. 실제 STT/LLM 연동 없이 docs/06 §9 응답 구조의 목데이터만 반환한다.
> TODO(AI 확정 후): LLM 호출, 컨텍스트(payload) 구성, 안전 원칙(docs/06 §11) 반영.

TODO 세 항목이 오늘 만든 것과 정확히 일치한다. 담당 파트의 실제 진입점은 여기다.

## 9. 다음에 할 일

우선순위 순.

1. **팀 main으로 이식** — `origin/main`에서 새 브랜치를 따고 로컬 로직을
   `com.cookpilot.backend.ai` 패키지에 옮긴다. 짜집기 필요(JdbcTemplate→JPA, 세션 모델 차이).
   시작 전에 팀의 `docs/06 §11` 안전 원칙을 읽고 프롬프트에 넣은 지침과 대조할 것.
2. **Gemini 실패 관측성** — `catch (Exception) → empty()`가 원인을 삼킨다. 3번의 전제 조건이다.
3. **안전 질문 실측** — 회의가 요구한 검증. 변질/알레르기/덜 익은 육류/기름불 각 카테고리 고정
   말뭉치로 `safety-intercept-enabled` on/off 응답 비교. LLM이 보수적 기준을 못 맞추면 기본값을
   `true`로 되돌린다. **실측 전에는 실서비스로 나가면 안 된다.**
4. `/ai/feedback`의 `Idempotency-Key` 부재, 서버측 호출 가드 부재 — LLM 호출 비중이 올라간 만큼
   중복 과금 위험도 커졌다.
5. **프론트 조율** — `LocalCoach`의 탄 것 패턴(`타고`)에 같은 누락이 있다. 그리고 프론트-백엔드가
   같은 판정을 각각 구현하고 말뭉치만 겹쳐 둔 상태라 또 갈라질 수 있다.
