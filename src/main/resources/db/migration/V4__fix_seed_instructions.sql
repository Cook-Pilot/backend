-- 이미 적용된 V3의 조리 문구 오타를 이력 보존 방식으로 수정한다.
UPDATE recipe_steps
SET instruction = '두부의 물기를 닦고 먹기 좋은 크기로 썰어주세요.'
WHERE id = '30000000-0000-0000-0000-000000000301';

UPDATE recipe_steps
SET instruction = '애호박, 양파, 두부를 한입 크기로 썰어주세요.'
WHERE id = '30000000-0000-0000-0000-000000000401';
