# refactor/recipe-edit-bean-validation

## 무엇을 왜

`POST /reviews/{reviewId}/personal-versions` 는 body 를 받는 엔드포인트 중 유일하게
`@Valid` 가 없었다. `RecipeEditRequest` 에 제약 어노테이션이 하나도 없어 null 항목·type
누락·공백 문자열·음수 값 같은 필드 단독 검증까지 전부 `PersonalRecipeService` 의 if-throw
로 손코딩돼 있었고, 그 결과 (1) OpenAPI 스키마에 제약이 드러나지 않고 (2) 에러가 한 번에
하나씩만 나갔다. 다른 body 엔드포인트 3곳(리뷰·AI 피드백·추천 피드백)은 이미 `@Valid` 를
쓴다 — 이 경로만 스프링답지 않았다.

## 핵심 설계 결정과 근거

- **필드 단독 검증만 어노테이션으로 옮겼다.** `@NotNull`(type, 리스트 요소),
  `@Pattern(.*\S.*, DOTALL)`(name/instruction — MODIFY 에서 null=유지가 정상이라
  `@NotBlank` 불가), `@PositiveOrZero`(timerSeconds), `@Min(-1)`(insertAfterStepIndex),
  `List<@Valid @NotNull …>`(리스트 안 null 항목).
- **type 조건부 규칙과 원본 대조는 서비스에 남겼다.** ADD↔원본 참조 상호배제, ADD 필수
  필드, 원본이 이 레시피 소속인지, 같은 원본 행 중복 조정, targetServings 요구 — 전부
  다른 필드·다른 행·리뷰 행을 봐야 판정되므로 Bean Validation 표준 어노테이션으로 표현
  불가. `@AssertTrue` 는 record 가 응답 DTO 겸용이라 직렬화에 가짜 필드가 새는 문제가
  있어 쓰지 않았다.
- **음수 amount 검사는 `IngredientAdjustmentInput.toAdjustment()` 로 이동.** amount 는
  키 생략/명시적 null/값 3상태 때문에 `JsonNode` 라 어노테이션이 못 닿는다. 숫자 타입
  검사가 이미 거기 있으므로 값 검사도 같은 곳에 뒀다.
- **에러 포맷 불변.** `GlobalExceptionHandler` 가 `MethodArgumentNotValidException` 을
  이미 400 ProblemDetail 로 내리고 있었다(필드별 메시지 병합). 메시지 문구도 기존 서비스
  문구를 그대로 어노테이션 message 로 옮겼다.

## 스키마/API 변경

- DB 변경 없음.
- HTTP 계약 동일(같은 입력 → 같은 400). 달라진 것: 필드 단독 위반은 이제 한 응답에
  여러 메시지가 병합돼 나가고, springdoc OpenAPI 스키마에 제약이 노출된다.

## 알려진 약점·후속

- `validate()` 는 미래 LLM 층(cooking/review) 출력의 신뢰 경계이기도 하다. 필드 단독
  검증이 요청 DTO 어노테이션으로 빠졌으므로, LLM 배관을 붙일 때 그 출력에는
  `Validator` 를 프로그램적으로 돌리거나 별도 검사가 필요하다 — `applyCooking` 의
  TODO 에 명시해 뒀다.
- 테스트는 기존 `RecipeEditPipelineApiTest` 가 상태 코드만 단언하므로 그대로 통과한다.
  검증 주체가 바뀐 것을 구분하는 새 테스트는 추가하지 않았다(계약이 같아서).
