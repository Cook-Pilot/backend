# feat/review-photos — 리뷰 사진 첨부

## 무엇을, 왜

조리 후 리뷰(`POST /api/v1/reviews`)에 사진 여러 장을 첨부할 수 있게 한다.
지금까지 리뷰는 텍스트(rating/comment/nextTimeNote)만 받았고, 이미지는 원본 레시피
쪽(`recipes.image_url`, `recipe_steps.image_url`)에만 있었다.

## 핵심 설계 결정

1. **URL만 받는다 — 파일 업로드 없음.** 원본 파일은 클라이언트가 외부 스토리지
   (Firebase 등)에 올리고 서버에는 URL 배열만 보낸다. 기존 `recipes.image_url`
   패턴과 동일하며, 서버 인프라(volume, 정적 서빙, 용량 관리) 변경이 없다.
2. **별도 테이블 없이 `post_cook_reviews.photo_urls` JSONB 컬럼** — 같은 테이블의
   `structured_feedback`이 이미 쓰는 `@JdbcTypeCode(SqlTypes.JSON)` 패턴 재사용.
   사진은 리뷰와 생명주기가 같고 개별 행으로 조회/조인할 일이 없어 자식 테이블이
   과하다(초기에 자식 테이블로 갔다가 단순화).
3. **순서는 클라이언트 배열 순서 그대로** — JSON 배열이 순서를 보존한다.
4. **리뷰는 append-only이므로 사진 수정 API 없음.** 리뷰 저장 시 한 번 받는 게 전부.
   멱등 재전송 시 기존 리뷰(기존 사진 포함)를 그대로 돌려준다 — 재시도 본문의
   photoUrls는 무시된다(기존 멱등 동작과 동일한 규칙).
5. **검증**: 항목당 `@NotBlank`, 최대 10장(`@Size`). URL 형식 검증은 하지 않는다 —
   스토리지가 확정되지 않아 패턴을 고정할 근거가 없다.

## 스키마/API 변경

- `V11__add_review_photos.sql`:
  `post_cook_reviews.photo_urls JSONB NOT NULL DEFAULT '[]'`
- `SubmitReviewRequest`: `photoUrls: List<String>` (선택, null = 사진 없음)
- `PostCookReview` 응답: `photoUrls: List<String>` (없으면 빈 배열)

## 알려진 약점·후속

- 스토리지 미확정: 클라이언트가 어디에 올릴지(Firebase/S3/기타) 결정되면 URL 도메인
  화이트리스트 검증 추가 고려.
- `GET /cooking-history`(`CookingHistoryItem`)에는 사진을 넣지 않았다 — 요청 범위 밖.
  필요해지면 추가.
