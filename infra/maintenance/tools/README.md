# 유지보수 도구

운영 카탈로그를 원문과 대조해 고치는 스크립트들. **임포터가 repo 에 없어서 운영 DB 가
유일본**이고, 이 도구들이 원문↔DB 를 잇는 유일한 경로다. 임포터를 새로 쓴다면 여기가 출발점이다.

전부 **일회성 유지보수용**이다. Flyway 마이그레이션이 아니고, 생성하는 SQL 은 감사 시점과
데이터가 다르면 **일부러 실패한다**(새 환경에서 재실행될 대상이 아니다 — 새 환경은 Flyway 시드
레시피가 8건뿐이다).

## 입력 파일 (repo 에 없다 — 직접 만든다)

스크립트들이 **현재 디렉터리에서** 읽는다. 커밋하지 않는다(운영 데이터라서).

### `source.json` — 원문 1,156행

```bash
FOODSAFETY_API_KEY=... python3 fetch_recipe_source.py
```

식품안전나라 `COOKRCP01`. 키는 공공데이터포털에서 발급한다.
**`sample` 키로는 안 된다** — 2026-08-19 확인 기준 요청 구간과 무관하게 늘 같은 5행만 온다
(`total_count` 는 1156 으로 정상 보고하므로 행 수를 세지 않으면 조용히 5건짜리 결과가 나온다).

### `details.json` — 현재 운영 DB 상태

레시피와 그 재료를 통째로 뽑은 것. 태그 백필은 `id`·`title` 만 쓰고, 재료 도구들이 나머지를 쓴다.

```sql
SELECT json_agg(x) FROM (
  SELECT r.id, r.title,
         COALESCE((SELECT json_agg(i ORDER BY i.sort_order)
                     FROM (SELECT id, name, amount, unit, sort_order, ingredient_group
                             FROM recipe_ingredients WHERE recipe_id = r.id) i), '[]') AS ingredients
    FROM recipes r ORDER BY r.title, r.id) x;
```

운영 접근은 AWS CLI + SSM 이다. **SSM 명령에 `$$`·`$X$` 같은 달러 인용을 넣으면 셸이 치환해
깨진다** — 확인 쿼리는 SQL 파일로 보낼 것.

### 중간 산출물

`rebuild.json`(이름 재구성 결과) · `group_assign.json`(그룹 매칭) · `tag_assign.json`(태그 부여) ·
`deferred.json`(보류 목록)은 아래 스크립트들이 만든다.

## 파이프라인

```
fetch_recipe_source.py                                        → source.json
parse_recipe_parts.py          (공용 파서 모듈. 직접 실행하지 않는다)

analyze_ingredient_names.py    source + details + rebuild      → deferred.json
extract_ingredient_groups.py   source + details + rebuild      → group_assign.json
extract_recipe_tags.py         source + details                → tag_assign.json

gen_ingredient_names_sql.py    details + rebuild               → 반영 SQL
gen_ingredient_groups_sql.py   details + group_assign          → 반영 SQL
gen_recipe_tags_sql.py         details + tag_assign            → 반영 SQL
```

`gen_*` 는 SQL 을 표준출력으로 뱉는다. `> ../2026-08-19-recipe-tags-backfill.sql` 처럼 받는다.

## 테스트

```bash
python3 test_extract_recipe_tags.py
```

태그 판정 규칙(제목 중복 처리·`기타` 미부여·사전에 없는 값)만 따로 굳혀 둔 것이라
원문도 DB 도 필요 없다. 여기서 틀리면 운영에 잘못된 태그가 들어간다.

`extract_recipe_tags.py` 는 다른 도구와 달리 **import 해도 부작용이 없다**(파일 읽기가
`__main__` 안에 있다). 나머지 도구는 최상위에서 파일을 읽어 import 만으로 죽는다 — 새로
만지는 도구는 이쪽 형태를 따를 것.

## 운영 반영 절차 (8/17 에 3회 모두 이렇게 했다)

1. **백업** — `manual/` 에 둔다. `db/` 는 14개 초과분을 자동 삭제하므로 중요한 스냅샷은 `manual/`.
2. **리허설** — 끝을 `ROLLBACK` 으로 바꿔 전제조건과 검증이 모두 통과하는지 본다.
3. **적용**
4. **전수 회귀 대조** — 반영 전후 스냅샷을 통째로 비교한다.

## 반드시 알 것

- **`personal_ingredient_adjustments.original_ingredient_id` 가 `recipe_ingredients(id)` 를
  `ON DELETE CASCADE` 로 참조한다.** 재료 행을 지우면 사용자 개인 레시피 수정이 조용히 함께
  지워진다. 8/17 의 `DELETE`+`INSERT` 는 그 테이블이 0행이라 안전했을 뿐이다. 스크립트가
  전제조건으로 검사해 거부한다.
- **원문↔DB 조인은 제목(`RCP_NM`)뿐이다.** 임포터가 `RCP_SEQ` 를 저장하지 않았다.
  제목이 겹치는 그룹이 있어서, 값이 갈리면 부여하지 않고 보류한다.
- **재료 `id` 의 UUIDv5 네임스페이스는 알 수 없다.** 8/17 에 재구성한 행만
  `uuid5(uuid5(URL,'https://cookpilot.app/recipe-ingredient'), '<recipe_id>:<sort_order>:<name>')`
  로 결정론적이다.
