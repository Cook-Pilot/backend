# F9 조리 완료 영속 도메인

## 무엇을 왜 바꾸는가

F9에서는 사용자가 후기를 작성하거나 건너뛰기 전에 조리 완료 사실을 먼저
보존한다. 선행 PR [#35](https://github.com/Cook-Pilot/backend/pull/35)는 이를
위한 PostgreSQL 컬럼과 lifecycle 제약을 추가했다.

이 PR은 그 저장 구조를 Java 도메인과 JPA entity에 연결한다.

- 프론트가 확정한 조리 완료 스냅샷을 검증하고 정규화한다.
- 같은 완료 사실의 재전송을 비교할 안정적인 fingerprint를 만든다.
- 완료 결과와 후기 lifecycle을 `PostCookReviewEntity`에 매핑한다.
- 기존 후기 저장 경로는 `FINALIZED + null payload` 형태로 보존한다.

완료·후기 HTTP API와 service 전환 로직은 포함하지 않는다. 이 PR은 후속 API가
사용할 저장 도메인까지만 다룬다.

## #35와의 의존 관계

#35가 추가한 컬럼은 하나의 완료 결과 bundle을 이룬다.

| 컬럼 | Java 매핑 | 의미 |
| --- | --- | --- |
| `cooking_result_schema_version` | `Short` | 완료 payload 계약 버전 |
| `cooking_result_payload` | `CookingResultPayload` | 프론트가 확정한 완료 사실 JSONB |
| `cooking_result_fingerprint` | `String` | schema v1 canonical projection의 SHA-256 |
| `review_status` | `ReviewLifecycleStatus` | `PENDING_REVIEW`, `FINALIZED`, `SKIPPED` |

이 PR에는 새 migration이 없다. #35의 V10/V11 migration과 CHECK constraint가 먼저
적용되어야 하므로 PR base도 #35의 head branch다.

## `CookingResultPayload` 계약

`CookingResultPayload`는 한 번의 조리가 끝난 시점의 불변 실행 사실이다.
개인 레시피 diff를 계산하거나 이슈 #34의 `setup`, `cooking`, `review` 변경안을
추론하지 않는다. `clientSessionId`도 payload 안에 중복 저장하지 않고 완료
리소스의 별도 멱등성 키로 관리한다.

### 최상위 필드

| 필드 | 규칙 |
| --- | --- |
| `recipeId` | 필수 |
| `cookedAt` | 필수, microsecond 정밀도로 정규화 |
| `sourcePersonalVersionId` | 선택 |
| `targetServings` | 필수, `0 < value <= 99.99`, 소수 둘째 자리까지 |
| `ingredients` | 0~100개 |
| `steps` | 1~100개 |

재료와 단계는 `sortOrder`로 정렬한 불변 목록으로 보존한다.

- `sortOrder`는 0 이상이며 목록 안에서 중복될 수 없다.
- `originalIngredientId`, `originalStepId`는 선택 값이지만, 값이 있으면 목록
  안에서 중복될 수 없다.
- 목록 자체와 목록 항목은 `null`일 수 없다.
- 입력 목록은 `List.copyOf()` 기반의 불변 값으로 복사한다.

### 재료 스냅샷

| 필드 | 규칙 |
| --- | --- |
| `name` | 필수, trim 후 1~200자 |
| `amount` | 선택, 0 이상, 정수 8자리·소수 18자리 범위 |
| `unit` | 선택, trim 후 최대 50자 |
| `required` | 프론트가 확정한 필수 여부 |
| `omitted` | 프론트가 확정한 생략 여부 |

`amount`는 trailing zero를 제거하고 정수의 음수 scale은 0으로 맞춘다.

### 단계 스냅샷

| 필드 | 규칙 |
| --- | --- |
| `instruction` | 필수, trim 후 1~4,000자 |
| `timerSeconds` | 선택, 값이 있으면 0 이상 |
| `cautionNote` | 선택, trim 후 최대 1,000자 |

현재 프론트 완료 스냅샷에 없는 단계 생략 여부 같은 값을 서버가 임의로 만들지
않는다.

## 문자열과 시간 정규화

### Dart `String.trim()` 호환

Java `String.trim()`이나 `String.strip()`은 Dart와 제거 대상이 다르다.
`DartStringTrim`은 프론트의 `String.trim()`과 같은 Unicode 경계 문자를 양끝에서
제거한다.

- 일반 공백과 탭·개행
- non-breaking space와 Unicode space separator
- line/paragraph separator
- BOM(`U+FEFF`)

Dart가 보존하는 `U+001C`~`U+001F` 같은 문자는 Java 구현도 제거하지 않는다.
선택 문자열은 trim 뒤 비면 `null`로 정규화하고, 필수 문자열은 비어 있으면
거부한다.

JSONB가 안전하게 왕복할 수 있도록 NUL 문자와 짝이 맞지 않는 UTF-16 surrogate도
거부한다. 문자열 최대 길이는 현재 Java `String.length()` 기준으로 검사한다.

### 완료 시각

`cookedAt`은 PostgreSQL `TIMESTAMP(6)`과 같은 microsecond 정밀도로 자른다.
허용 범위는 `0001-01-01T00:00:00Z`부터
`9999-12-31T23:59:59.999999Z`까지다.

## Schema v1 fingerprint

fingerprint는 JSON 문자열을 직접 해시하지 않는다. serializer의 필드 순서,
공백, 숫자 출력 방식에 따라 결과가 달라지지 않도록 정규화된 도메인 값을 별도의
binary projection으로 기록한 뒤 SHA-256을 계산한다.

projection에는 다음 경계가 포함된다.

- domain marker `CPCR`
- schema version `1`
- nullable 값의 presence marker
- UTF-8 문자열의 byte length
- 목록 길이와 각 항목의 정렬된 전체 필드
- UUID의 두 64-bit 값
- 완료 시각의 epoch second와 microsecond
- 정규화된 decimal의 plain string

결과는 소문자 64자리 SHA-256 문자열이다. 비교할 때는 두 입력의 형식을 먼저
검사하고 `MessageDigest.isEqual()`을 사용한다. 지원하지 않는 schema version은
fingerprint를 만들기 전에 거부한다.

## Entity와 lifecycle

`PostCookReviewEntity.pendingCookingResult()`는 `userId`,
`clientSessionId`, `CookingResultPayload`를 받아 다음 값을 한 번에 만든다.

- 관계형 검색용 `recipeId`, `cookedAt`, `sourcePersonalVersionId`,
  `targetServings`
- typed JSONB `cookingResultPayload`
- schema version과 fingerprint
- `PENDING_REVIEW` 상태
- 비어 있는 별점·후기·다음 메모

관계형 필드와 JSONB를 같은 payload에서 복사하므로 서로 다른 요청 값이 섞이지
않는다. 완료 결과 bundle 컬럼은 JPA에서 `updatable = false`로 매핑해 최초 저장
뒤 수정하지 않는다.

기존 생성자와 기존 `POST /api/v1/reviews` 경로는 그대로 유지한다.

- 기본 lifecycle은 `FINALIZED`
- 완료 결과 schema version, payload, fingerprint는 `null`
- 기존 별점·후기·다음 메모와 `structured_feedback` 동작은 변경하지 않음

`SKIPPED` enum은 저장 모델에 포함되지만, 실제 finalize/skip 상태 전환은 후속
service와 API PR에서 구현한다.

## 검증

비컨테이너 단위 테스트 108개를 실행한다.

| 테스트 | 개수 | 검증 범위 |
| --- | ---: | --- |
| `CookingResultPayloadTest` | 38 | 필드 경계, 정규화, 정렬, Unicode |
| `CookingResultFingerprintTest` | 36 | golden hash, 모든 필드 민감도, 형식과 비교 |
| `DartStringTrimTest` | 31 | Dart trim 공유 케이스와 보존 문자 |
| `PostCookReviewEntityTest` | 3 | pending 생성, 레거시 호환, 필수 값 |

`CoreSchemaPersistenceTest`에는 PostgreSQL 16 Testcontainers 왕복 테스트 1개를
추가했다. 저장 뒤 persistence context를 비우고 다시 읽어 다음을 확인한다.

- typed JSONB payload
- schema version과 fingerprint
- `clientSessionId`, `recipeId`, `cookedAt`, `targetServings`
- `PENDING_REVIEW` lifecycle
- `rating`이 `null`인지

로컬 환경에 Docker가 없으면 이 테스트는 실행할 수 없으므로 PR CI의 Docker
환경을 최종 gate로 사용한다.

## 이 PR에 포함되지 않는 것

- completion, finalize, skip HTTP API
- service lifecycle 전환과 동시성 처리
- 새로운 repository query 또는 read model
- 개인 레시피 diff 추론이나 자동 적용
- 프론트엔드 변경

후속 PR은 이 저장 도메인 위에서 조회 경계, 후기 terminal 전환, completion
service와 HTTP 계약을 검토 가능한 크기로 순서대로 연결한다.
