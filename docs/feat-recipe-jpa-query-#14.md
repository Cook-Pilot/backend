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

## 코드 흐름 이해

### 목록 조회: 기본 정보만 가져온다

`RecipeService.findAll()`은 활성 레시피를 조회한 뒤 목록 전용 `RecipeOverview`로 변환한다.

```java
public List<RecipeOverview> findAll() {
    return recipeRepository.findByStatus("active").stream()
            .map(this::toOverview)
            .toList();
}
```

`RecipeOverview`에는 목록 응답에 필요한 값만 있다.

```java
public record RecipeOverview(
        UUID id,
        String title,
        String description,
        String imageUrl
) {
}
```

기존 `Recipe`는 `ingredients`, `steps`까지 포함하므로 목록에서 사용하면 필요 없는 DB 조회와
객체 생성이 발생한다. 목록과 상세의 용도를 분리하면 코드만 읽어도 목록에서는 기본 정보만
필요하다는 사실을 알 수 있다.

전체 흐름은 다음과 같다.

```text
GET /api/v1/recipes
→ RecipeController.list()
→ RecipeService.findAll()
→ recipes 테이블에서 active 레시피 조회
→ RecipeOverview 목록 생성
→ 개인 레시피 최신 버전 배치 조회
→ RecipeSummaryResponse 생성
```

### 목록의 개인 레시피는 한 번에 조회한다

목록 응답에는 사용자가 해당 레시피의 개인 버전을 가지고 있는지도 포함된다. 레시피마다
`findLatestByRecipe()`를 호출하면 레시피 개수만큼 쿼리가 추가되는 N+1 문제가 생긴다.

```java
Map<UUID, PersonalRecipeVersion> latestByRecipe =
        personalRecipeService.findLatestByRecipes(recipeIds);
```

`findLatestByRecipes()`는 여러 레시피 ID를 `IN` 조건으로 한 번에 조회한다. 결과는 버전 번호가
큰 순서이며, `putIfAbsent()`로 레시피별 첫 번째 버전만 보관한다.

```java
versionRepository.findByUserIdAndRecipeIdInOrderByVersionNumberDesc(userId, recipeIds)
        .forEach(entity -> latestByRecipe.putIfAbsent(
                entity.getRecipeId(), PersonalRecipeVersion.from(entity)));
```

따라서 목록에서는 재료·단계 쿼리를 실행하지 않으면서 개인 레시피 배지는 N+1 없이 유지한다.

### 상세 조회: 재료와 단계를 조합한다

`RecipeService.findById()`는 먼저 원본 레시피를 조회한다. 결과가 없으면
`NotFoundException`을 발생시키고 기존 예외 처리기가 404로 변환한다.

```java
RecipeEntity entity = recipeRepository.findById(recipeId)
        .orElseThrow(() -> new NotFoundException(
                "레시피를 찾을 수 없습니다: " + recipeId));
```

레시피가 있으면 상세 응답에 필요한 재료와 조리 단계를 각각 조회한다.

```java
recipeIngredientRepository.findByRecipeIdOrderBySortOrderAsc(recipeId);
recipeStepRepository.findByRecipeIdOrderByStepIndexAsc(recipeId);
```

Spring Data JPA가 메서드 이름을 해석하므로 별도 SQL 구현 없이 다음 규칙이 적용된다.

- 재료: `recipe_id`가 일치하는 행을 `sort_order` 오름차순으로 반환
- 단계: `recipe_id`가 일치하는 행을 `step_index` 오름차순으로 반환

마지막으로 Entity를 외부 API용 record로 변환한다.

```text
RecipeEntity
+ RecipeIngredientEntity 목록
+ RecipeStepEntity 목록
→ Recipe
→ JSON 상세 응답
```

`RecipeEntity`를 Controller가 직접 반환하지 않으므로 DB 컬럼 변경이 곧바로 JSON 계약 변경으로
이어지는 것을 막는다.

### Repository 필드명과 생성자 주입

`RecipeService`는 프로젝트의 기존 스타일에 맞춰 Repository 타입 이름을 필드명에 그대로 반영한다.

```java
private final RecipeRepository recipeRepository;
private final RecipeIngredientRepository recipeIngredientRepository;
private final RecipeStepRepository recipeStepRepository;
```

Spring이 생성한 Repository Bean을 생성자로 주입받고, `private final`로 보관한다. 서비스가
직접 Repository 구현체를 만들지 않으므로 의존 관계가 명확하고 테스트에서도 교체하기 쉽다.

### 테스트가 보장하는 것

`RecipeApiTest`는 `PostgresApiTestBase`를 상속해 Testcontainers PostgreSQL과 실제 Flyway
스키마를 사용한다.

- 목록은 `id`, `title`, 개인 버전 정보를 반환하고 `ingredients`, `steps`는 노출하지 않는다.
- Java 코드에 하드코딩되지 않은 레시피를 DB에 저장해도 목록과 상세에서 조회된다.
- 재료를 역순으로 저장해도 `sort_order` 순서로 반환된다.
- 단계를 역순으로 저장해도 `step_index` 순서로 반환된다.
- 타이머, 주의사항, 이미지가 API 모델에 빠짐없이 매핑된다.
- 존재하지 않는 `recipeId`는 404를 반환한다.

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
