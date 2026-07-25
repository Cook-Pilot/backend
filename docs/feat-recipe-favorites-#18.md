# #18 사용자별 레시피 즐겨찾기

`recipe_favorites`는 사용자와 레시피의 즐겨찾기 관계를 저장한다.
`(user_id, recipe_id)`를 유일하게 만들어 중복 저장을 막는다.

| Method | URL | 역할 |
| --- | --- | --- |
| `GET` | `/api/v1/favorites` | 현재 사용자의 즐겨찾기 조회 |
| `PUT` | `/api/v1/recipes/{recipeId}/favorite` | 즐겨찾기 추가 |
| `DELETE` | `/api/v1/recipes/{recipeId}/favorite` | 즐겨찾기 해제 |

추가와 해제는 같은 요청을 반복해도 같은 최종 상태가 되며, 신규 사용자는
빈 목록을 받는다. 목록 조회 시 레시피와 개인 레시피 존재 여부를 묶어서 조회한다.
