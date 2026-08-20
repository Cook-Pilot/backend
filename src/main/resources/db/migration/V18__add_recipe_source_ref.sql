-- 레시피 원본 출처 추적 (#85, #77 리뷰 회의 결정).
--
-- 임포터가 원문 고유번호(RCP_SEQ)를 버리고 저장해서, 원문과 DB 를 잇는 열쇠가
-- 제목 문자열뿐이었다. 같은 비용을 두 번 치렀다(2026-08-17 재료 복구, #77 태그 백필).
-- 원본에서 끌고 온 레시피가 원본과 달라졌을 때 되짚어갈 수 있도록, 내부 식별자(UUID)
-- 옆에 **남이 발급한 번호**를 나란히 기록한다. 우리가 발급하는 번호(자동증가 등)로는
-- 풀리지 않는 문제다 — 재적재하면 바뀌고, 원본과의 대응 정보가 애초에 없다.
--
-- 유튜브·블로그 등 다음 소스도 같은 구조를 쓴다: source_type 이 어느 세계의 번호인지,
-- source_ref 가 그 세계에서의 번호인지를 말한다.
--
-- 번호가 V18 인 이유: V17 은 열려 있는 #75 가 선점했다(머지 순서 75 → 77).

ALTER TABLE recipes
  ADD COLUMN source_type TEXT,
  ADD COLUMN source_ref  TEXT;

-- 둘 다 있거나 둘 다 없거나. 손으로 만든 레시피(데모 시드 등)는 둘 다 NULL 이다.
ALTER TABLE recipes
  ADD CONSTRAINT recipes_source_pair_check
  CHECK ((source_type IS NULL) = (source_ref IS NULL));

-- 같은 원본을 두 번 적재하지 못하게 한다. NULL(출처 없음)은 여럿일 수 있으므로 부분 인덱스.
CREATE UNIQUE INDEX uq_recipes_source
  ON recipes (source_type, source_ref)
  WHERE source_type IS NOT NULL;
