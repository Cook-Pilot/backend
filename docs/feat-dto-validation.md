# feat/dto-validation — 요청 DTO에 Bean Validation 도입

## 무엇을 왜

`spring-boot-starter-validation` 의존성은 있었지만 실제로는 어디서도 쓰지 않았다. 필수값 검증이
컨트롤러 수동 null 체크(Review, AiFeedback)와 서비스 수동 체크(RecommendationFeedback)로
흩어져 있었고, Swagger 스펙에는 어떤 필드가 필수인지 전혀 드러나지 않았다.

이 브랜치는 **기존 수동 체크가 정의하던 기준을 그대로** `@NotNull`/`@NotBlank` 어노테이션으로
옮긴다. 새 검증 규칙을 추가하지 않는다 — 선언 위치만 바꾼다.

## 핵심 설계 결정

1. **단순 presence 체크만 어노테이션으로.** null/blank 체크는 DTO 어노테이션 + 컨트롤러 `@Valid`로.
   DB 조회가 필요한 검증(레시피 존재, 재료 소속, diff 정합성)은 기존대로 서비스에 남긴다.
2. **에러 메시지 유지.** 기존 수동 체크의 한국어 메시지("recipeId는 필수입니다." 등)를 어노테이션
   `message` 속성으로 그대로 이전. 클라이언트가 보는 문구 변화 없음.
3. **응답 포맷 유지.** `GlobalExceptionHandler`에 `MethodArgumentNotValidException` 핸들러를 추가해
   기존과 같은 400 + RFC-7807 `ProblemDetail`로 내린다. 필드 에러가 여럿이면 메시지를 공백으로 join.
4. **`RecipeEditRequest`는 손대지 않음.** `setup`/`cooking` 레이어가 둘 다 nullable인 것은 설계
   의도(레이어 선택적 전달)이고, 심층 검증은 `PersonalRecipeService`가 담당한다.

## 변경 내역

| 파일 | 변경 |
|---|---|
| `SubmitReviewRequest` | `@NotNull` clientSessionId, recipeId, rating |
| `ReviewController.submit` | `@Valid` 추가, 수동 null 체크 3개 제거 |
| `AiFeedbackRequest` (인라인 record) | `@NotNull` recipeId, stepIndex / `@NotBlank` userSpeech |
| `AiFeedbackController.feedback` | `@Valid` 추가, 수동 체크 3개 제거 |
| `SubmitRecommendationFeedbackRequest` | `@NotNull` ×5, `@NotBlank` promptVersion |
| `RecommendationController.feedback` | `@Valid` 추가 |
| `RecommendationFeedbackService` | 필수값 null 체크 블록 제거 (어노테이션이 대체) |
| `GlobalExceptionHandler` | `MethodArgumentNotValidException` → 400 ProblemDetail 핸들러 추가 |

스키마/DB 변경 없음. API 계약 변화 없음(같은 입력 → 같은 400).

## 부수 효과

springdoc이 `@NotNull`/`@NotBlank`를 읽어 OpenAPI 스펙에 `required` 필드로 자동 표시한다 —
Flutter 클라이언트가 스펙만 보고 필수 필드를 알 수 있게 됨.

## 알려진 약점·후속

- `ReviewService`의 rating 범위(1~5), targetServings > 0 체크는 서비스에 그대로 있다.
  `@Min`/`@Max`/`@Positive`로 옮길 수 있지만 이번 범위에서 제외 (기준 변경 없음 원칙).
- 검증 실패 메시지 순서는 필드 순서에 따라 달라질 수 있다 (여러 필드 동시 누락 시).
- `@Schema(requiredMode)` 명시는 하지 않았다 — springdoc의 어노테이션 자동 인식에 의존.
