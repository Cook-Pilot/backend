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
                     FROM (SELECT ri.id, ing.name, ri.amount, ri.unit, ri.sort_order, ri.ingredient_group
                             FROM recipe_ingredients ri
                             JOIN ingredients ing ON ing.id = ri.ingredient_id
                            WHERE ri.recipe_id = r.id) i), '[]') AS ingredients
    FROM recipes r ORDER BY r.title, r.id) x;
```

V19(재료 정규화) 이후의 SQL 이다 — 이름이 `ingredients` 마스터로 옮겨져 조인이 필요하다.
V19 가 아직 안 붙은 환경에서는 조인 없이 `recipe_ingredients.name` 을 직접 읽는다.

운영 접근은 AWS CLI + SSM 이다. **SSM 명령에 `$$`·`$X$` 같은 달러 인용을 넣으면 셸이 치환해
깨진다** — 확인 쿼리는 SQL 파일로 보낼 것.

### 중간 산출물

`rebuild.json`(이름 재구성 결과) · `group_assign.json`(그룹 매칭) · `tag_assign.json + source_ref.json`(태그 부여) ·
`deferred.json`(보류 목록)은 아래 스크립트들이 만든다.

## 파이프라인

```
fetch_recipe_source.py                                        → source.json
parse_recipe_parts.py          (공용 파서 모듈. 직접 실행하지 않는다)

analyze_ingredient_names.py    source + details + rebuild      → deferred.json
extract_ingredient_groups.py   source + details + rebuild      → group_assign.json
extract_recipe_tags.py         source + details                → tag_assign.json + source_ref.json

fetch_recipe_details.py        (공개 API)                       → full.json
classify_recipe_cuisine_occasion.py   full                      → assign.json

gen_ingredient_names_sql.py    details + rebuild               → 반영 SQL
gen_ingredient_groups_sql.py   details + group_assign          → 반영 SQL
gen_recipe_tags_sql.py         details + tag_assign + source_ref → 반영 SQL
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


## 출처 백필 (#85)

`extract_recipe_tags.py` 는 제목 매칭의 부산물로 `source_ref.json` 도 만든다 —
제목이 원문에서 유일한 레시피의 `(recipe_id, RCP_SEQ)` 쌍이다. `gen_recipe_tags_sql.py` 가
같은 SQL 안에 `recipes.source_type/source_ref` 백필 섹션을 넣는다(V18 컬럼).
이걸 한 번 반영하고 나면 다음 유지보수부터는 제목 매칭 없이 `source_ref` 로 원문을 찾는다.


## 문화권·용도 분류 (2026-08-21 적용 완료)

`classify_recipe_cuisine_occasion.py` 는 앞의 도구들과 입력이 다르다. **운영 DB 덤프도
원문 API 키도 쓰지 않고 공개 API 만 쓴다**(`fetch_recipe_details.py`). 이 두 축은 제목·
요리종류·열량만 보므로 재료의 양·단위가 필요 없고, 목록·상세 API 는 게스트에 열려 있다(#76).

원문에서 유도할 수 있는 음식형태·조리법과 달리 **문화권·용도는 원문에도 DB 에도 값이 없다.**
그래서 규칙으로 판단하고, 확신이 서지 않으면 비운다 — 문화권은 1,150건 중 533건을 일부러
비워 뒀다. 잘못 붙은 태그는 없는 것보다 나쁘다(누르면 엉뚱한 목록이 나온다).

### 적용 결과

| 축 | 부여 | 내역 |
| --- | --- | --- |
| CUISINE | 617 | 한식 395 · 양식 169 · 퓨전 22 · 중식 15 · 일식 10 · 아시안 6 |
| OCCASION | 531 | 다이어트 214 · 간식 172 · 안주 49 · 야식 28 · 손님상 21 · 도시락 18 · 명절 17 · 해장 12 |

`assigned_by='RULE'` 로 남겼다. V21 이 넣은 `IMPORT` 와 구분되므로 **이 부여만 정확히
지울 수 있다** — 실제로 적용 중 실수로 지웠다가 그대로 복구했다.

```sql
DELETE FROM recipe_tags WHERE assigned_by = 'RULE';
```

### 표본 검증에서 잡은 것

규칙을 처음 돌린 결과를 그냥 믿었으면 그대로 들어갔을 오류들이다. **부분 문자열 매칭**이
공통 원인이다.

- `밀라노 스타일 포크 커틀렛` → 안주 (`포`(육포)가 **포**크에 걸림)
- `가지 탕수육` → 손님상 (`수육`이 탕**수육**에 걸림)
- `불고기덮밥` → 손님상 (`불고기`는 일상 반찬에도 흔해 잔치 신호가 못 됨)

짧은 단어마다 "이 문자열 안에 있으면 매칭이 아니다"라는 예외(`TRAPS`)를 함께 둔다.

### 아직 비어 있는 태그

`아이반찬`·`초스피드`·`혼밥` 은 0건이다.

- **아이반찬** — 제목에 '아이'가 든 6건만 잡혀 뺐다. 순한 반찬을 가르는 규칙을 못 만들었다.
- **초스피드** — 1,150건 중 1,036건이 6단계이고 단계 타이머는 **전량 0** 이다. 빠른 요리를 가릴 신호가 없다.
- **혼밥** — 규칙을 못 만들었다.

화면에 칩으로 내보내려면 `is_active=false` 로 내려야 한다. 눌렀을 때 빈 목록이 나오는 것은
V14 가 미리 적어 둔 실패 방식이다.
