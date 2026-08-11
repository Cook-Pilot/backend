# feat/review-photos-s3 — 리뷰 사진 업로드 실제 S3 전환

이슈 #52. `POST /api/v1/reviews/photos` 의 목 구현(#50)을 실제 S3 업로드로 바꾼다.

## 무엇을, 왜

#50 은 스토리지가 없어 저장 없이 목 URL 만 돌려줬다. 인프라(버킷·권한)가 준비되어
실제 업로드로 교체한다. **API 계약은 그대로다** — 요청/응답 형태가 같아
`docs/openapi.json` 변경이 없고, 프론트는 코드를 고칠 필요가 없다.

## 핵심 설계 결정

1. **버킷 미설정 = 목 유지.** `cookpilot.photos.bucket`(`PHOTOS_BUCKET`)이 비어 있으면
   업로드 없이 목 URL 을 돌려준다. 로컬 개발·CI 는 AWS 자격증명 없이 그대로 돌아간다
   (Gemini 키 미설정 시 폴백과 같은 방식). 운영만 env 를 채운다.
2. **자격증명을 코드·환경변수에 두지 않는다.** EC2 인스턴스 역할(`cookpilot-ec2-ssm`)을
   SDK default credential chain 이 자동으로 집는다. 액세스 키를 발급·보관하지 않으므로
   유출 경로 자체가 없다. 컨테이너에서 IMDSv2 접근은 실측 확인했다.
3. **`S3Client` 는 `@Lazy` + `ObjectProvider`.** 버킷이 설정된 환경에서만 실제로
   만들어진다. 목으로 도는 로컬/테스트에서는 빈 생성 자체가 일어나지 않는다.
4. **content-type 화이트리스트(jpeg/png/webp/heic) 로 확장자를 정한다.** content-type 은
   클라이언트 신고값이라 위조할 수 있지만, 최소한 스크립트·실행파일이 이미지인 척
   올라오는 건 막는다. 매직바이트 검사는 지금 규모에 과하다고 보고 하지 않았다.
5. **키는 `review-photos/{uuid}.{확장자}`.** 버킷 정책이 이 접두사만 공개 읽기로 열어둔
   상태라 접두사가 계약이다. 원본 파일명은 쓰지 않는다(중복·경로 주입·한글 인코딩 회피).

## 설정/배포 변경

- `application.yml`: `cookpilot.photos.bucket`(기본 빈값), `cookpilot.photos.region`(기본 `ap-northeast-2`)
- `docker-compose.prod.yml` app: `PHOTOS_BUCKET`, `PHOTOS_REGION` 전달
- `.env.example`: 두 키 추가 (자격증명은 넣지 않는다는 주석 포함)
- `build.gradle`: `software.amazon.awssdk:bom:2.35.9` + `s3`

## 그 외 변경

- `MaxUploadSizeExceededException` → **413**. 이전에는 10MB 초과가 500 으로 나갔다.
- 업로드 실패는 `UncheckedIOException`(→500). `IllegalStateException` 은 핸들러가 409 로
  매핑하므로 저장소 장애에 쓰면 의미가 어긋난다.

## 운영 인프라 (참고)

- 버킷 `cookpilot-photos-167403240280`(ap-northeast-2). `review-photos/*` 만 공개 읽기,
  목록 조회(ListBucket)는 403. 쓰기는 인스턴스 역할만.
- nginx `client_max_body_size 12m` — Spring 한도(10MB)보다 크게 잡아 한도 초과가
  nginx 413 이 아니라 앱의 413 메시지로 나가게 했다.

## 알려진 약점·후속

- **목 URL 이 저장된 기존 리뷰**는 깨진 채로 남는다. 베타 데이터라 감수할지 정리할지
  결정 필요(이슈 #52 검증 항목).
- **업로드된 사진과 리뷰의 연결 검증 없음** — 업로드한 사람과 리뷰 작성자가 같은지,
  올린 URL 을 실제로 썼는지 확인하지 않는다. 고아 객체(업로드 후 리뷰 미제출)도
  정리하지 않는다. 필요해지면 수명주기 규칙(예: 태그 + S3 Lifecycle)으로 다룬다.
- **삭제 경로 없음.** 리뷰가 append-only 라 사진도 지우지 않는다.
- 트래픽이 늘면 presigned URL(클라이언트가 S3 로 직접 업로드)로 바꿔 서버 대역폭을
  아끼는 게 확장 경로다. 지금은 서버 경유가 단순해서 이대로 둔다.

## AI 리뷰 반영 (Copilot)

- **SDK 예외까지 잡는다.** `IOException` 만 잡고 있어 자격증명·권한·네트워크 실패(`SdkException`)가
  그대로 새면 응답 형식이 제각각이 된다. 둘 다 `PhotoUploadFailedException` 으로 감싸 500 +
  고정 문구로 내리고, 원인은 로그로만 남긴다(자격증명·ARN 이 클라이언트로 새지 않게).
- **테스트에 버킷을 빈 값으로 고정**(`@TestPropertySource`). 개발자 환경에 `PHOTOS_BUCKET` 이
  설정돼 있어도 테스트가 실제 S3 로 새지 않는다.
- **413·500 매핑 회귀 테스트 추가**(`UploadErrorMappingTest`). MockMvc 는 multipart 파싱을
  거치지 않아 크기 초과를 재현할 수 없어 핸들러 매핑을 직접 검증한다(실서버 11MB 실측은 별도 확인).
