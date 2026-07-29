# F9 조리결과 lifecycle 저장 스키마

## 무엇을 왜 바꾸는가

기존 `post_cook_reviews`는 조리완료 사실과 후기를 한 번에 저장한다. F9에서는
프론트가 조리를 끝낸 직후 완료 결과를 먼저 저장하고, 사용자가 후기를 확정하거나
건너뛰는 시점을 별도로 기록해야 한다.

이 브랜치는 후속 API가 그 lifecycle을 안전하게 구현할 수 있도록 다음 데이터를
추가한다.

- 완료 결과 JSON의 계약 버전
- 프론트가 보낸 불변 완료 결과 JSON
- 동일 재전송을 판별할 SHA-256 fingerprint
- `PENDING_REVIEW` / `FINALIZED` / `SKIPPED` 후기 상태

완료 JSON은 프론트가 알고 있는 실행 사실의 정본이다. 개인 레시피 diff나 이슈
#34의 `setup` / `cooking` / `review` 입력이 아니며, 이 migration에서 내용을
추론하거나 가공하지 않는다.

## 핵심 설계 결정

### 기존 행과 구버전 서버를 보존한다

기존 행은 이미 완료와 후기가 함께 저장된 기록이므로 다음 상태로 유지한다.

- `review_status = 'FINALIZED'`
- 완료 결과 bundle 3개 컬럼은 모두 `NULL`
- 기존 별점·후기·메모·구조화 결과는 변경하지 않음

`review_status`의 기본값도 `FINALIZED`로 둔다. 배포 중 구버전 서버가 기존 INSERT를
계속 수행하더라도 실패하지 않게 하기 위한 expand 단계다. 후속 완료 API는 상태와
bundle을 항상 명시적으로 저장한다.

### bundle은 전부 있거나 전부 없어야 한다

`cooking_result_schema_version`, `cooking_result_payload`,
`cooking_result_fingerprint`는 하나의 bundle이다.

- 레거시 행: 세 값이 모두 `NULL`
- 신규 행: schema version은 `1`, payload는 JSON object, fingerprint는 소문자
  64자리 SHA-256

부분적으로 채워진 완료 결과나 지원하지 않는 schema version은 DB에서도 거부한다.

### lifecycle 상태별 불변식을 DB에서 지킨다

- `PENDING_REVIEW`, `SKIPPED`는 완료 결과 bundle이 반드시 필요하다.
- `PENDING_REVIEW`, `SKIPPED`에는 별점·후기·다음 메모가 없어야 한다.
- 비종료 상태의 `structured_feedback`은 빈 JSON object여야 한다.
- `FINALIZED`의 후기 필드는 모두 선택 사항이다.
- 레거시 `FINALIZED + NULL bundle`은 계속 허용한다.

애플리케이션 검증이 누락되거나 동시 요청이 발생해도 lifecycle에 맞지 않는 데이터가
저장되지 않게 하는 마지막 방어선이다.

### 제약 추가와 기존 행 검증을 분리한다

V10은 네 제약을 `NOT VALID`로 추가하고 V11이 별도 migration에서
`VALIDATE CONSTRAINT`를 수행한다.

이렇게 하면 제약을 추가할 때 필요한 짧은 강한 잠금과 기존 행을 스캔하는 시간을
분리할 수 있다. PostgreSQL의 constraint validation은 일반 읽기·쓰기를 계속
허용하지만, 실제 운영 테이블 크기에 따라 스캔 시간과 I/O는 배포 전에 관찰해야 한다.

## 스키마 변경

| 컬럼 | 타입 | 의미 |
| --- | --- | --- |
| `cooking_result_schema_version` | `SMALLINT` | 완료 JSON 계약 버전, 레거시는 `NULL` |
| `cooking_result_payload` | `JSONB` | 프론트가 보낸 불변 조리완료 사실 |
| `cooking_result_fingerprint` | `TEXT` | versioned projection의 소문자 SHA-256 |
| `review_status` | `TEXT NOT NULL` | `PENDING_REVIEW`, `FINALIZED`, `SKIPPED` |

추가되는 CHECK constraint는 다음과 같다.

- `ck_reviews_cooking_result_bundle`
- `ck_reviews_review_status`
- `ck_reviews_pending_or_skipped_requires_result`
- `ck_reviews_non_finalized_review_data_empty`

## 검증

`CookingResultStorageMigrationUpgradeTest`가 실제 PostgreSQL 16에서 다음 순서를
검증한다.

1. Flyway를 V9까지만 적용한다.
2. 후기와 실행 정보가 있는 레거시 행을 삽입한다.
3. V10 적용 뒤 구버전 컬럼 목록으로 새 행을 삽입해 rolling 배포 호환성을 검증한다.
4. V10 전후 레거시 행 보존과 신규 상태별 제약을 검증한다.
5. 잘못된 bundle·상태·비종료 후기 데이터가 정확한 CHECK constraint로 거부되는지
   확인한다.
6. V10에서는 네 제약이 아직 미검증 상태인지 확인한다.
7. V11 적용 후 네 제약이 모두 validated 상태인지 확인한다.

실행 명령:

```bash
./gradlew test --tests \
  com.cookpilot.backend.persistence.CookingResultStorageMigrationUpgradeTest
./gradlew test
```

이 테스트는 Testcontainers를 사용하므로 Docker가 필요하다. 로컬 Docker를 사용할
수 없는 환경에서는 컴파일과 비컨테이너 테스트를 먼저 확인하고, PR CI의 Docker
환경을 최종 migration gate로 사용한다.

## 알려진 약점과 후속

- 이 브랜치는 스키마만 추가한다. Java entity mapping, payload DTO, fingerprint
  생성, completion/review API는 검토 가능한 크기로 나눈 후속 PR에서 연결한다.
- `review_status` 기본값 제거 여부는 구버전 서버가 더 이상 배포되지 않는 시점에
  별도로 결정한다.
- V11 validation은 일반 DML을 막지 않지만 테이블 전체를 읽는다. 운영 데이터가
  커지면 배포 전후 validation 시간과 DB I/O를 관찰해야 한다.
- Flyway migration은 적용 후 수정하지 않는다. 배포 뒤 문제가 발견되면 기존
  migration을 되돌려 쓰지 않고 새 forward-fix migration을 추가한다.
