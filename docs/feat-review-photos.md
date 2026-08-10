# feat/review-photos — 리뷰 사진 첨부

## 무엇을, 왜

조리 후 리뷰(`POST /api/v1/reviews`)에 사진 여러 장을 첨부할 수 있게 한다.
지금까지 리뷰는 텍스트(rating/comment/nextTimeNote)만 받았고, 이미지는 원본 레시피
쪽(`recipes.image_url`, `recipe_steps.image_url`)에만 있었다.

## 핵심 설계 결정

1. ~~**URL만 받는다 — 파일 업로드 없음.**~~ → **이슈 #48로 방향 변경: 서버 업로드
   엔드포인트 추가.** 클라이언트가 `POST /reviews/photos`로 파일을 올리고 URL을 받아
   `POST /reviews`의 photoUrls에 넣는다. 단, S3가 아직 없어 지금은 목 URL만 돌려준다
   (아래 "업로드 엔드포인트" 절).
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

## 업로드 엔드포인트 (이슈 #48)

`POST /api/v1/reviews/photos` — multipart/form-data, `file` 파트 1개, 201 + `{"url": "..."}`.

- **한 장씩 받는다.** 여러 장이면 장수만큼 반복 호출. 이유: 실패/재시도 단위가
  장 단위로 깔끔하고(여러 장 multipart는 부분 실패 응답이 애매), 클라이언트가
  병렬로 쏘면 속도 손해도 없다. 단점은 왕복 N번뿐 — 사진 몇 장 수준에선 무시.
- **S3 미확정 → 목 구현.** `ReviewPhotoService`가 저장 없이
  `https://mock-storage.cookpilot.local/review-photos/{uuid}`를 돌려준다.
  AI 목 패턴과 동일한 `TODO(S3 확정 후)` 방식. 클라이언트는 업로드→URL→리뷰 제출
  흐름을 지금부터 붙일 수 있다.
- 검증: 빈 파일·비이미지 content-type → 400. 파일당 10MB
  (`spring.servlet.multipart.max-file-size`, 스프링 기본 1MB는 사진에 부족).

## 스키마/API 변경

- `V11__add_review_photos.sql`:
  `post_cook_reviews.photo_urls JSONB NOT NULL DEFAULT '[]'`
- `SubmitReviewRequest`: `photoUrls: List<String>` (선택, null = 사진 없음)
- `PostCookReview` 응답: `photoUrls: List<String>` (없으면 빈 배열)

- `POST /reviews/photos`: 위 "업로드 엔드포인트" 절 참고.

## 알려진 약점·후속

- 스토리지 미확정: S3 확정되면 `ReviewPhotoService` 목을 실제 업로드로 교체하고
  URL 도메인 화이트리스트 검증 추가 고려.
- 업로드된 사진과 리뷰의 연결 검증 없음: 목 단계라 어떤 URL이든 photoUrls에 넣을 수
  있다. 실제 스토리지 붙일 때 같이 판단.
- `GET /cooking-history`(`CookingHistoryItem`)에는 사진을 넣지 않았다 — 요청 범위 밖.
  필요해지면 추가.
