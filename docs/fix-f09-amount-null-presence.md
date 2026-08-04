# F-09 재료 양 제거 계약 보존

## 무엇을 왜

프론트는 개인 버전 `MODIFY`에서 재료 양을 지울 때 `"amount": null`을 보내고, 양을
건드리지 않았을 때는 `amount` 키를 생략한다. 기존 백엔드는 둘을 모두 Java/DB `null`로
합쳐서 항상 "원본 양 유지"로 처리했다. 요청은 성공하지만 사용자의 양 제거가 저장되지 않는
조용한 데이터 오류였다.

이번 변경은 `amount`의 세 상태를 요청부터 조회까지 보존한다.

| 요청 | 의미 | 저장 |
|---|---|---|
| `amount` 키 생략 | 원본 양 유지 | `amount_override_present=false` |
| `"amount": null` | 양 제거 | `amount_override_present=true`, `amount=NULL` |
| `"amount": 1.5` | 1.5로 변경 | `amount_override_present=true`, `amount=1.5` |

## 핵심 설계 결정

### 요청 DTO와 저장 도메인을 분리

Spring Boot 4의 Jackson 3에서 nullable `BigDecimal` record component는 키 생략과 명시적
`null`을 모두 Java `null`로 만든다. 요청 전용 `IngredientAdjustmentInput`의 `amount`만
`JsonNode`로 받아 키 생략(Java `null`)과 명시적 null(`NullNode`)을 구분한 뒤,
`IngredientAdjustment(amountSpecified)`로 변환한다.

커스텀 역직렬화기나 Jackson 2 기반 nullable 라이브러리를 추가하지 않았다. OpenAPI에는
`JsonNode` 구현 스키마를 노출하지 않고 `number | null`로 고정했다.

### DB에 presence를 별도 저장

`personal_ingredient_adjustments.amount`의 `NULL` 하나로는 유지와 제거를 동시에 표현할 수
없으므로 `amount_override_present BOOLEAN NOT NULL DEFAULT FALSE`를 추가했다.

- 기존 non-null `MODIFY` 행은 `true`로 backfill한다.
- 애플리케이션은 `MODIFY + non-null amount`를 flag와 무관하게 오버라이드로 읽는다.
  이전 바이너리나 롤백 writer가 기본값 `false`로 저장해도 값 변경이 사라지지 않는다.
- `true`는 `MODIFY`에만 허용하는 CHECK를 둔다. 반대로 "non-null이면 반드시 true"인 CHECK는
  이전 writer를 깨뜨리므로 두지 않았다.

### 합성과 no-op 판정

`DiffComposer`는 `amountSpecified=true`면 값이 null이어도 원본을 덮어쓴다. no-op 판정도
명시적 null과 원본 null은 같다고 보고, 명시적 null과 원본 숫자는 실제 변경으로 본다.
인분 정규화 과정에서도 presence flag를 그대로 전달한다.

## 스키마/API 변경

- Flyway `V10__preserve_ingredient_amount_presence.sql`
- 요청 스키마 `IngredientAdjustmentInput.amount`: `number | null`
- 상세 응답의 원시 diff에 읽기 전용 `amountSpecified` 추가
- 기존 숫자 전송과 키 생략 의미는 그대로라 기존 클라이언트와 호환된다.

## 검증

- Jackson 요청 단위 테스트: 생략 / null / 숫자 / 잘못된 문자열
- 순수 합성 테스트: 생략은 유지, null은 제거
- PostgreSQL API 테스트: null 저장·조회 및 인분 정규화 후 presence 보존
- OpenAPI 테스트: nullable 계약, read-only flag, `JsonNode` 스키마 비노출

## 알려진 약점·배포 순서

- 과거에 의미 없이 항상 `"amount": null`을 보내던 외부 클라이언트가 있다면 배포 후에는
  양 제거로 해석된다. 현재 Flutter 클라이언트는 `includeAmount`로 키 포함을 의도적으로
  제어한다.
- 이전 백엔드 바이너리로 롤백하면 새 명시적-null 행을 일시적으로 원본 유지로 렌더링한다.
  presence 데이터는 DB에 남아 다시 새 버전을 배포하면 복구된다.
- 백엔드를 먼저 배포한 뒤 해당 Flutter 변경을 배포해야 한다. 반대 순서에서는 프론트가
  보낸 명시적 null을 구버전 백엔드가 원본 유지로 저장한다.
