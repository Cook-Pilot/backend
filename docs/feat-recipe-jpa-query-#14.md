# F-01 레시피 조회 API JPA 전환

## 무엇을 왜 바꿨나

기존 `RecipeService`는 라면과 김치볶음밥을 `LinkedHashMap`에 직접 넣고 반환했다.
Flyway V2가 같은 데이터를 PostgreSQL에 저장해도 조회 API는 DB를 읽지 않아, 새 레시피를
추가하려면 Java 코드를 수정해야 했다.

이번 작업은 `GET /api/v1/recipes`와 `GET /api/v1/recipes/{recipeId}`가 PostgreSQL의
`recipes`, `recipe_ingredients`, `recipe_steps`를 조회하도록 전환한다.

## 핵심 설계 결정

### Entity와 응답 모델을 분리한다

- `RecipeEntity`, `RecipeIngredientEntity`, `RecipeStepEntity`: DB 행과 매핑한다.
- `RecipeOverview`: 목록에 필요한 기본 정보만 Service와 Controller 사이에서 전달한다.
- `Recipe`, `RecipeIngredient`, `RecipeStep`: 상세 API 응답에 사용하는 불변 record다.
- `RecipeService.toRecipe()`가 Entity를 응답 모델로 변환한다. Entity를 Controller 밖으로 직접 노출하지 않는다.

### 조회는 읽기 전용 트랜잭션이다

`RecipeService`에 `@Transactional(readOnly = true)`를 적용한다. 조회 의도를 명확히 하고
JPA가 불필요한 변경 감지를 하지 않도록 한다.

### 목록과 상세의 조회 범위를 분리한다

목록 응답에는 레시피의 기본 정보만 필요하므로 재료와 조리 단계를 조회하지 않는다.

1. 활성 레시피 목록 조회
2. `RecipeOverview` 목록으로 변환
3. 현재 사용자의 개인 버전을 `IN`으로 일괄 조회
4. Controller에서 기존 `RecipeSummaryResponse`로 조합

상세 조회에서만 해당 레시피의 재료와 조리 단계를 각각 조회한다. Repository 메서드 이름에
`OrderBySortOrderAsc`, `OrderByStepIndexAsc`를 명시해 기존 정렬 계약을 유지한다.

### 데모 UUID는 테스트로 이동한다

운영 `RecipeService`는 특정 라면·볶음밥 ID를 알지 않는다. Flyway V2 seed 고정 ID는
`TestRecipeIds`에서만 테스트 fixture로 참조한다.

## 스키마와 API 변경

- Flyway 스키마 변경: 없음
- API 경로 변경: 없음
- JSON 필드 변경: 없음
- 기존 404 `ProblemDetail` 계약 유지

## 검증

- Testcontainers PostgreSQL에만 저장한 레시피가 목록과 상세 API에서 조회되는지 확인한다.
- 재료 `sort_order`와 단계 `step_index` 순서를 확인한다.
- 설명, 대표 이미지, 수량, 타이머, 주의사항, 단계 이미지 매핑을 확인한다.
- AI 피드백 API 테스트도 실제 PostgreSQL 레시피를 사용한다.
- 전체 `./gradlew test`가 통과한다.

## 알려진 약점과 후속

- 개인 버전 일괄 조회는 요청된 레시피들의 버전을 모두 읽고 Java에서 최신 버전을 고른다.
  데이터가 커지면 DB window function 또는 `is_default` 전용 조회로 최적화할 수 있다.
- 기본 무프로파일 실행은 health/context 확인용이다. 레시피처럼 DB가 필요한 API는 `db` 프로파일이 필요하다.
