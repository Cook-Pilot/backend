# chore/apply-lombok

## 무엇을 왜

수동 DI 생성자 보일러플레이트를 Lombok `@RequiredArgsConstructor` 로 대체. Lombok 은 이미 `build.gradle` 에 있었지만(annotationProcessor 포함) 실제 사용처가 0건이었다. 생성자 19개, −153/+38 줄.

## 핵심 결정과 근거

- **순수 할당 생성자만 대체.** 생성자가 로직을 가지면 제외:
  - `GeminiRecommendationExplanationClient` — RestClient/스키마 조립 로직
  - `CookingCoachClient` — `ObjectProvider.getIfAvailable()` 분기
  - `ReviewPhotoService` — 파라미터에 `@Value` 가 붙어 있어 `@RequiredArgsConstructor` 로 옮기려면 `lombok.config`(copyableAnnotations) 가 추가로 필요. 설정 파일 하나 늘리는 것보다 수동 생성자 유지가 단순.
- 엔티티/예외/record 는 대상 아님 — 엔티티 생성자는 도메인 생성자(JPA 기본 생성자 + 의미 있는 인자)라 DI 보일러플레이트가 아니다.
- `RecommendationDraftLoader` 는 패키지-프라이빗 클래스지만 Lombok 이 만드는 public 생성자로 문제 없음(클래스 가시성이 게이트).

## 스키마/API 변경

없음. 순수 내부 리팩터링 — 컴파일 결과 동작 동일.

## 알려진 약점·후속

- 이후 새 빈은 `@RequiredArgsConstructor` + `private final` 컨벤션.
- `feat/social-login`(PR #56) 은 이 브랜치 이후 머지되므로 auth 패키지는 머지 후 같은 방식 적용 필요.
