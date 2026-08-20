-- 태그 부여 테이블의 두 구멍을 막는다. 둘 다 recipe_tags 가 0 행인 지금이 가장 싸다
-- (부여가 시작되면 제약 추가에 기존 행 검증·정리가 따라붙는다).
--
-- 1. 파생 태그가 recipe_tags 에 저장되는 것을 막는다.
-- 2. 태그 code 개명 경로를 연다 (ON UPDATE CASCADE).

-- ── 1. 파생 태그는 부여할 수 없다 ────────────────────────────────────────────
--
-- tags.match_rule 이 채워진 태그는 조회 시점에 계산하는 파생 태그이고, recipe_tags 에
-- 행을 만들지 않는 것이 V14 의 계약이다('두부'는 recipe_ingredients 에 이미 있고,
-- '쉬움'은 수치에서 유도해야 정렬이 된다). 그런데 V14 의 FK 는 (code, axis_code) 만 봐서
-- 파생 태그를 넣는 것을 막지 못했다. 들어가면 재료·난이도가 바뀔 때 사본이 어긋나고
-- 계산된 칩과 중복된다.
--
-- 부여 가능 여부를 컬럼으로 드러내고 그것까지 FK 에 포함시켜, '파생 태그는 부여 불가'를
-- 애플리케이션이 아니라 DB 가 강제하게 한다.
--
-- 생성 컬럼(GENERATED ALWAYS AS)이 아니라 일반 컬럼 + CHECK 를 쓴다. 참조되는 쪽이
-- 생성 컬럼일 때의 동작이 버전마다 미묘하고, CHECK 로도 match_rule 과의 일치는 똑같이 강제된다.
ALTER TABLE tags
  ADD COLUMN is_assignable BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE tags
  ADD CONSTRAINT tags_assignable_matches_match_rule
    CHECK (is_assignable = (match_rule IS NULL));

-- 복합 FK 의 대상. code 가 이미 PK 라 논리적으로는 잉여이고, 오직 아래 FK 가 참조할
-- 대상을 만들기 위해 존재한다 (V14 의 UNIQUE (code, axis_code) 와 같은 이유다).
ALTER TABLE tags
  ADD CONSTRAINT tags_code_axis_assignable_key UNIQUE (code, axis_code, is_assignable);

-- 부여 행은 항상 TRUE 여야 한다. CHECK 가 FALSE 를 원천 차단하고, FK 가 그 TRUE 를
-- 사전의 is_assignable 과 맞춘다. 둘이 합쳐져야 '파생 태그 금지'가 성립한다.
ALTER TABLE recipe_tags
  ADD COLUMN is_assignable BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE recipe_tags
  ADD CONSTRAINT recipe_tags_only_assignable CHECK (is_assignable);

-- ── 2. FK 교체 (+ ON UPDATE CASCADE) ────────────────────────────────────────
--
-- V14 의 FK 는 인라인으로 만들어 이름이 자동 생성됐다. 이름을 추측해 박으면 환경에 따라
-- 깨지므로 카탈로그에서 찾아 지운다.
DO $migration$
DECLARE
  existing_fk TEXT;
BEGIN
  SELECT conname INTO existing_fk
  FROM pg_constraint
  WHERE conrelid = 'recipe_tags'::regclass
    AND confrelid = 'tags'::regclass
    AND contype = 'f';

  IF existing_fk IS NOT NULL THEN
    EXECUTE format('ALTER TABLE recipe_tags DROP CONSTRAINT %I', existing_fk);
  END IF;
END
$migration$;

-- ON UPDATE CASCADE 를 붙이는 이유.
--
-- tags 는 대리키(뜻 없는 id)를 두지 않고 code 자체를 PK 로 쓴다. code 가 API 파라미터
-- (?tags=CUISINE_KOREAN)로 그대로 나가서 URL 이 읽히고 공유·딥링크가 되는 값이기 때문이다
-- (경쟁사 조사에서 우리의식탁이 태그를 URL 에 남기지 않아 딥링크·SEO 를 포기한 사례가 있다).
--
-- 자연키의 통상적인 단점은 '개명하면 참조가 전부 깨진다'인데, ON UPDATE CASCADE 가
-- 그 비용을 없앤다 — UPDATE tags SET code = ... 한 번이면 recipe_tags 가 따라온다.
-- V14 는 이걸 빼먹어서 자연키를 쓰면서 완화 장치는 없는 상태였다.
--
-- 밖으로 나간 code 를 바꾸면 옛 URL 이 깨지는 것은 여전하다. 그건 대리키를 썼어도
-- 노출한 값(숫자)에 똑같이 생기는 비용이라, 키 선택으로 없앨 수 있는 종류가 아니다.
ALTER TABLE recipe_tags
  ADD CONSTRAINT recipe_tags_tag_fkey
    FOREIGN KEY (tag_code, axis_code, is_assignable)
      REFERENCES tags (code, axis_code, is_assignable)
    ON UPDATE CASCADE;
