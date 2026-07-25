# #16 사용자 최근 조리 레시피

## 목적

홈 화면에서 현재 사용자가 실제로 완료한 최근 조리 레시피를 조회한다.

## 데이터 기준

별도 서버 조리 세션을 만들지 않고, 조리 완료 후 저장되는
`post_cook_reviews`를 완료 이력으로 사용한다.

## API

```http
GET /api/v1/home/recent-recipes
X-CookPilot-User-Id: <user UUID>
```

## 조회 규칙

1. 현재 사용자의 리뷰만 최신순으로 조회한다.
2. 같은 레시피를 여러 번 조리한 경우 최신 기록 한 건만 남긴다.
3. 서로 다른 최근 레시피를 최대 10개 반환한다.
4. 레시피가 비활성화됐거나 삭제됐다면 결과에서 제외한다.
5. 신규 사용자처럼 이력이 없으면 오류가 아닌 `[]`를 반환한다.

## 응답 조합

홈 화면이 별도 추가 조회 없이 표시할 수 있도록 레시피 요약과 다음 정보를 반환한다.

- 최근 조리 시각
- 개인 레시피 보유 여부
- 최신 개인 레시피 버전 ID
- 즐겨찾기 여부

레시피와 개인 버전·즐겨찾기 상태는 ID 집합으로 배치 조회해
항목마다 DB를 다시 조회하는 N+1을 피한다.

## 주요 변경 파일

- `home/HomeRecipeController.java`
- `home/HomeRecipeService.java`
- `home/RecentRecipeResponse.java`
- `review/ReviewRepository.java`
- 개인 레시피·즐겨찾기 배치 조회 Repository

## 검증

PostgreSQL Testcontainers API 테스트에서 다음을 확인한다.

- 최신순 정렬
- 동일 레시피 중복 제거
- 최대 10개 제한
- 사용자별 데이터 분리
- 신규 사용자 빈 배열
- 개인 버전·즐겨찾기 상태 조합
- Gradle 전체 테스트와 빌드

## 제외

조리 중 서버 세션 저장과 리뷰 작성 화면은 이 이슈에 포함하지 않는다.
