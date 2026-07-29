# 개인 버전 실행 스냅샷 정규화

## 배경

개인 레시피 버전은 원본 레시피에 대한 누적 diff로 저장된다. `REMOVE`된 원본
재료·단계는 개인 버전을 합성해 프론트에 전달할 때 결과 목록에서 사라진다.

따라서 사용자가 다음 개인 버전을 조리했다고 가정하면:

1. 원본 단계 `B`를 제거한다.
2. 제거된 `B` 뒤에 새 단계 `X`를 추가한다.
3. 프론트는 합성된 실행 목록 `A → X → C`를 후기 요청에 보낸다.

기존 서버는 원본 `B`가 요청에 없다는 이유로 불완전한 스냅샷으로 판단해
`400 Bad Request`를 반환했다. 소스 개인 버전에 이미 명시적인 `REMOVE(B)`가
있다는 점을 실행 결과 정규화에 반영하지 않았기 때문이다.

## 변경 내용

후기 요청의 `sourcePersonalVersionId`가 유효하면 해당 버전의 누적 재료·단계
조정 내역을 실행 스냅샷 정규화에 함께 사용한다.

- 소스 버전에 명시적인 `REMOVE`가 있는 원본만 스냅샷에서 빠질 수 있다.
- 소스에서 제거되지 않은 활성 원본이 빠지면 기존처럼 400으로 거부한다.
- 소스의 `REMOVE`, `MODIFY`, `ADD` 메타데이터를 유지해 동일 실행을 새 버전으로
  중복 저장하지 않는다.
- 재료 또는 단계 중 한쪽 스냅샷만 전송되면, 보내지 않은 쪽은 소스 버전의
  누적 diff를 그대로 유지한다.
- 소스 버전은 현재 사용자 소유이고 현재 레시피에 속하는지 검증한 뒤 사용한다.

## 추가 단계 위치 정책

원본 단계가 제거되면 그 뒤에 있던 `ADD` 단계의 앵커는 합성 결과만으로 복원할 수
없다. 데모 단계에서는 다음 B 정책을 적용한다.

- 실행 스냅샷의 보이는 단계 구조가 소스 버전과 같으면 소스 `ADD`의
  `insertAfterStepIndex`와 `sortOrder`를 위치별로 재사용한다.
- 설명·타이머·주의 문구만 달라졌다면 같은 위치로 보고 기존 앵커를 유지한다.
- 원본 또는 추가 단계의 배치가 달라진 구조 변경이면 소스 `ADD` 메타데이터를
  부분적으로 섞지 않고, 현재 실행 순서를 기준으로 모든 활성 `ADD`의 위치를
  다시 계산한다.

구조 변경 때 전체 위치를 다시 계산하는 이유는 기존 앵커와 새 앵커를 섞을 경우
동일 앵커·동일 `sortOrder` 충돌로 단계 순서가 뒤집힐 수 있기 때문이다. 향후
개인 버전 항목에 안정적인 식별자나 리비전 모델을 도입하면 항목 단위 위치 보존을
다시 검토할 수 있다.

## 스냅샷 안전성 계약

이번 변경은 부분 페이로드를 암묵적인 삭제로 허용하지 않는다.

| 입력 | 처리 |
|---|---|
| 소스에 명시적인 `REMOVE`가 있고 해당 원본이 누락됨 | 소스 `REMOVE`로 복원 |
| 활성 원본이 누락됨 | 400 |
| 실행하지 않은 활성 원본을 `omitted=true`로 보냄 | 새 `REMOVE`로 계산 |
| 재료·단계를 모두 보내지 않음 | 후기만 저장, 새 버전 미생성 |
| 한쪽 스냅샷만 보냄 | 보낸 쪽만 계산하고 반대쪽 소스 diff 유지 |
| 소스 개인 버전과 실행 결과가 동일함 | 중복 개인 버전 미생성 |

## 변경하지 않는 범위

- DB 스키마와 Flyway 마이그레이션
- API 경로 및 요청·응답 DTO
- 프론트엔드 프로덕션 코드
- 개인 버전 승인 및 기본 버전 지정 정책
- F9 제안 생성·승인 워커

## 변경 파일

- `PersonalRecipeService.java`
  - 소스 버전 조정 내역을 포함한 실행 스냅샷 정규화
  - source-aware `REMOVE` 복원
  - `ADD` 위치·순서 재사용 및 구조 변경 시 재계산
- `ReviewFlowApiTest.java`
  - 소스 `REMOVE`/`ADD` 동일 실행 중복 방지
  - 활성 원본 재료·단계 누락 400
  - 한쪽 스냅샷 전송 시 반대쪽 소스 diff 보존
  - 제거된 단계 뒤 `ADD` 앵커 보존
  - 구조 변경 시 현재 실행 위치 사용
  - 여러 `ADD`의 위치별 순서 보존

## 검증

로컬에서 확인한 항목:

```bash
./gradlew test --no-daemon \
  --tests 'com.cookpilot.backend.personalrecipe.DiffComposerTest' \
  --tests 'com.cookpilot.backend.recommendation.RecommendationRuleEngineTest*' \
  --tests 'com.cookpilot.backend.recommendation.RecommendationWiringTest' \
  --tests 'com.cookpilot.backend.recommendation.explanation.GeminiApiTest' \
  --tests 'com.cookpilot.backend.recommendation.explanation.RecommendationExplanationServiceTest'
./gradlew build -x test --no-daemon
git diff --check
```

- Docker 비의존 테스트 46개 통과
- 백엔드 빌드 통과
- 새 PostgreSQL 통합 테스트 컴파일 통과
- 코드·성능·테스트 리뷰 통과

로컬 Docker를 사용할 수 없어 `ReviewFlowApiTest`의 실제 PostgreSQL 실행은
GitHub Actions CI에서 최종 확인한다.
