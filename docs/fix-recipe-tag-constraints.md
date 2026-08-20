# 태그 부여 제약 보강 (V17)

`V14` 태그 스키마가 남긴 구멍 두 개를 막는다. 설계 배경과 왜 그런 구조인지는
[`feat-recipe-tags.md`](feat-recipe-tags.md) 에 있다.

## 왜 지금인가

**`recipe_tags` 가 0행인 지금이 가장 싸다.** 둘 다 제약 추가라, 부여가 시작된 뒤에는
기존 행 검증과 정리가 따라붙는다. 다음 작업이 원문 백필(약 2,000행)이라 그 전에 넣어야 한다.

## 담은 것

### 1. 파생 태그를 부여할 수 없게 한다

`tags.match_rule` 이 채워진 태그는 **조회 시점에 계산하는 파생 태그**이고 `recipe_tags` 에
행을 만들지 않는 것이 V14 의 계약이다(`두부` 는 `recipe_ingredients` 에 이미 있고, `쉬움` 은
수치에서 유도해야 정렬이 된다). 그런데 V14 의 FK 는 `(code, axis_code)` 만 봐서 파생 태그를
넣는 것을 막지 못했다. 들어가면 재료·난이도가 바뀔 때 사본이 어긋나고 계산된 칩과 중복된다.

부여 가능 여부를 컬럼으로 드러내고 **FK 에 포함**시켜 DB 가 강제하게 한다.

```sql
tags         is_assignable BOOLEAN NOT NULL DEFAULT TRUE
             CHECK (is_assignable = (match_rule IS NULL))
             UNIQUE (code, axis_code, is_assignable)      -- FK 대상용
recipe_tags  is_assignable BOOLEAN NOT NULL DEFAULT TRUE
             CHECK (is_assignable)                        -- 항상 TRUE
             FOREIGN KEY (tag_code, axis_code, is_assignable)
               REFERENCES tags(code, axis_code, is_assignable)
```

`recipe_tags` 의 CHECK 가 FALSE 를 원천 차단하고, FK 가 그 TRUE 를 사전의 값과 맞춘다.
둘이 합쳐져야 성립한다. 부수 효과로 **이미 부여된 태그를 파생으로 바꾸는 것도 막힌다**(그렇게
되면 기존 행이 조용히 무효가 되므로, 실패하는 편이 맞다).

`GENERATED ALWAYS AS` 생성 컬럼이 아니라 일반 컬럼 + CHECK 를 썼다. 참조되는 쪽이 생성
컬럼일 때의 동작이 버전마다 미묘한데, CHECK 로도 `match_rule` 과의 일치는 똑같이 강제된다.

`is_assignable` 역시 `axis_code` 와 같은 종류의 의도된 중복이다(사전만 보면 알 수 있는 값을
연결 테이블에 복제). 같은 이유로 복합 FK 가 사본의 정합을 보장한다.

### 2. `ON UPDATE CASCADE` — 자연키의 대가를 되사온다

`tags` 는 대리키를 두지 않고 `code` 를 PK 로 쓴다(근거는 `feat-recipe-tags.md`). 자연키의
통상적 단점은 "개명하면 참조가 전부 깨진다" 인데, V14 는 FK 에 `ON UPDATE` 를 안 걸어서
기본값 `NO ACTION` 이었다 — **개명이 아예 거부된다.**

FK 를 다시 만들면서 `ON UPDATE CASCADE` 를 붙였다. 이제 `UPDATE tags SET code = …` 한 번이면
`recipe_tags` 가 따라온다.

밖으로 나간 `code` 를 바꾸면 옛 URL 이 깨지는 것은 여전하다. 그건 대리키를 썼어도 노출한
값(숫자)에 똑같이 생기는 비용이라 키 선택으로 없앨 수 있는 종류가 아니다.

## 기존 FK 이름을 추측하지 않는다

V14 의 FK 는 인라인으로 만들어 이름이 자동 생성됐다(`recipe_tags_tag_code_axis_code_fkey`).
이름을 박아 넣으면 환경에 따라 깨질 수 있어 `pg_constraint` 에서 찾아 지운다.

## 검증

`RecipeTagSchemaTest` 에 5건 추가했다. 이 테스트가 보는 것은 늘 그렇듯 **어떤 잘못된 데이터가
DB 에 못 들어가는가** 다.

- 파생 태그는 붙지 않는다 / 저장형 태그는 그대로 붙는다(제약이 정상 부여까지 막지 않는지)
- 부여된 태그를 파생으로 바꿀 수 없다
- 사전의 `is_assignable` 이 `match_rule` 과 어긋날 수 없다
- 태그 코드를 바꾸면 부여된 행이 따라온다(CASCADE)

## API 영향

**없다.** 태그는 아직 엔티티·DTO 가 없어 응답에 나가지 않는다. `ddl-auto: validate` 는
매핑되지 않은 컬럼을 문제 삼지 않는다.
