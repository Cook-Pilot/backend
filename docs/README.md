# CookPilot 확정 MVP 산출물

작성 기준: 2026-07-10  
목표 일정: 2026-07-26 동작 가능한 앱 데모

이 폴더는 2026-07-10 대화에서 확정한 CookPilot MVP 기준으로 기존 산출물 형식의 `01`~`07` 문서를 다시 작성한 버전입니다. 기존 상위 `outputs` 폴더의 원본 문서는 덮어쓰지 않았습니다.

## 파일 구성

| 파일 | 용도 |
|---|---|
| `01_requirements.md` | 요구사항 정리 / 문제 정의 / 사용자 / 지표 |
| `02_function_spec.md` | 기능 명세서 / 기능 ID / 수락 기준 |
| `03_mvp_design.md` | MVP 범위 / 음성 조리 모드 / 데모 기준 |
| `04_screen_structure.md` | 화면 구조 / 사용자 흐름 / 예외 상태 |
| `05_db_schema.sql` | PostgreSQL 기준 DB 초안 DDL |
| `06_tech_stack_and_architecture.md` | 기술 스택 / 아키텍처 / AI 처리 방식 |
| `07_development_roadmap.md` | 개발 순서 / 스프린트 / 2026-07-26 체크리스트 |

## 확정 MVP 핵심

```text
조리 화면 한정 STT/TTS 음성 조리 모드
+ 로컬 Recipe Runner
+ 로컬 Command Router
+ 예외 상황 LLM 피드백
+ 조리 후 피드백
+ 개인 레시피 버전 업데이트
+ 다음 조리 개선 루프
```

