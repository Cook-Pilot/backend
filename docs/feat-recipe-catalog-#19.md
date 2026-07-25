# #19 기본 레시피 카탈로그 확장

## 목적

프론트가 하드코딩 레시피 없이 동작하도록 Flyway V3에서 1~2인 가구용
기본 레시피 6종을 추가한다. 기존 라면·김치볶음밥을 포함해 총 8종이다.

## 추가 카탈로그

- 두부조림
- 된장찌개
- 계란볶음밥
- 제육볶음
- 오일파스타
- 닭갈비

각 레시피는 `recipes`, `recipe_ingredients`, `recipe_steps`에
고정 UUID와 정렬 순서를 가진 seed 데이터로 저장한다.

## Flyway 변경

- `V3__expand_recipe_catalog.sql`
  - 레시피·재료·조리 단계 추가
- `V4__fix_seed_instructions.sql`
  - 이미 적용된 V3를 수정하지 않고 후속 마이그레이션으로 잘못된 조리 문구 보정

Flyway 이력을 유지해야 하므로 적용된 마이그레이션을 직접 수정하지 않는다.

## API 동작

- `GET /api/v1/recipes`
  - 활성 레시피를 제목 오름차순으로 안정적으로 반환
- `GET /api/v1/recipes/{recipeId}`
  - DB의 `base_servings`를 `baseServings`로 포함
  - 재료는 `sort_order`, 조리 단계는 `step_index` 순서 유지
  - 없는 UUID는 기존처럼 404 반환

## 주요 변경 파일

- `src/main/resources/db/migration/V3__expand_recipe_catalog.sql`
- `src/main/resources/db/migration/V4__fix_seed_instructions.sql`
- `src/main/java/com/cookpilot/backend/recipe/Recipe.java`
- `src/main/java/com/cookpilot/backend/recipe/RecipeController.java`
- `src/main/java/com/cookpilot/backend/recipe/RecipeRepository.java`

## 검증

PostgreSQL Testcontainers를 사용해 다음을 확인한다.

- 총 8개 레시피 목록
- 제목순 정렬
- 기준 인분 응답
- 재료와 조리 단계 순서
- 상세 404
- Gradle 전체 테스트와 빌드
