-- 과일갈비찜의 181px 단계 이미지를 GPT 복원본으로 교체한다.
-- 현재 원본 URL까지 조건에 포함해, 운영 데이터가 먼저 변경된 경우 덮어쓰지 않는다.

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/5afcb0e3-fe06-4f7e-86c6-3223cb80d57b.jpg'
WHERE id = '89bf9c3b-5fbc-563e-a49d-78c95fdf5137'
  AND recipe_id = '41133cf4-371b-5448-9e36-823379baa2d8'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00551_1.png';

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/bde9d45c-b124-4f4c-9bde-743fa4777bf2.jpg'
WHERE id = '85e90ad6-334f-58c7-b649-0a30cbb142f1'
  AND recipe_id = '41133cf4-371b-5448-9e36-823379baa2d8'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00551_2.png';

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/10ca3c81-8a5d-47e7-a317-dd0cb090488f.jpg'
WHERE id = 'dd138f18-27ac-56ea-8be2-a33ebb5fb40d'
  AND recipe_id = '41133cf4-371b-5448-9e36-823379baa2d8'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00551_3.png';

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/c98a1db0-cbf5-4c0c-abda-fa1cd9573cc3.jpg'
WHERE id = '64460519-de32-5777-ac5d-dc8bd7d55c43'
  AND recipe_id = '41133cf4-371b-5448-9e36-823379baa2d8'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00551_4.png';

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/211ee173-09db-4984-b880-7ba829178d72.jpg'
WHERE id = '81a9c524-483f-5774-932e-a71d56b0cd1e'
  AND recipe_id = '41133cf4-371b-5448-9e36-823379baa2d8'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00551_5.png';

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/0b38730b-3b6d-4de8-afda-2536a6fc6f2e.jpg'
WHERE id = 'd8234cf8-f416-55c7-81af-486ba0a7c53d'
  AND recipe_id = '41133cf4-371b-5448-9e36-823379baa2d8'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00551_6.png';
