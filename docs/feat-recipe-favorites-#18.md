# #18 사용자별 레시피 즐겨찾기

## 목적

현재 사용자가 레시피를 저장하고 홈에서 다시 조회할 수 있도록
사용자와 레시피 사이의 즐겨찾기 관계를 영속화한다.

## DB

`recipe_favorites`는 사용자와 레시피의 즐겨찾기 관계를 저장한다.
`(user_id, recipe_id)`를 유일하게 만들어 중복 저장을 막는다.

주요 컬럼:

- `id`: 즐겨찾기 행 UUID
- `user_id`: 사용자 FK
- `recipe_id`: 레시피 FK
- `created_at`: 등록 시각

사용자나 레시피가 삭제되면 관계도 함께 정리되도록 FK를 구성한다.

## API

| Method | URL | 역할 |
| --- | --- | --- |
| `GET` | `/api/v1/favorites` | 현재 사용자의 즐겨찾기 조회 |
| `PUT` | `/api/v1/recipes/{recipeId}/favorite` | 즐겨찾기 추가 |
| `DELETE` | `/api/v1/recipes/{recipeId}/favorite` | 즐겨찾기 해제 |

## 동작 규칙

- 같은 PUT을 반복해도 즐겨찾기 행은 한 개만 존재한다.
- 동시 PUT도 PostgreSQL `ON CONFLICT DO NOTHING`으로 한 행만 저장한다.
- 이미 해제된 항목에 DELETE를 반복해도 최종 상태는 해제로 유지된다.
- 신규 사용자는 `GET /favorites`에서 `[]`를 받는다.
- 존재하지 않는 레시피는 404를 반환한다.
- 사용자별로 조회 조건을 분리해 다른 사용자의 즐겨찾기가 노출되지 않는다.

레시피 목록 응답에도 `favorite`을 포함해 프론트가 각 항목의 상태를
추가 요청 없이 표시할 수 있게 한다.
최근 조리 응답에도 같은 사용자의 `favorite`을 배치로 조합한다.

## 조회 성능

즐겨찾기 목록과 레시피 목록을 조합할 때 레시피 ID 집합으로
즐겨찾기·개인 버전을 배치 조회한다. 목록 항목 수만큼 쿼리가 증가하는 N+1을 피한다.

## 주요 변경 파일

- `db/migration/V5__create_recipe_favorites.sql`
- `favorite/FavoriteEntity.java`
- `favorite/FavoriteRepository.java`
- `favorite/FavoriteService.java`
- `favorite/FavoriteController.java`
- 레시피 목록 응답의 `favorite` 조합

## 검증

- 사용자별 즐겨찾기 분리
- 신규 사용자 빈 목록
- 등록·중복 등록
- 동시 등록
- 해제·중복 해제
- 없는 레시피 404
- 목록의 `favorite` 상태
- 최근 조리의 `favorite` 상태
- PostgreSQL Testcontainers
- Gradle 전체 테스트와 빌드
