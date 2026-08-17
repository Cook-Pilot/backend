-- L.A 갈비구이의 181px 단계 이미지를 GPT 복원본으로 교체한다.
-- 현재 원본 URL까지 조건에 포함해, 운영 데이터가 먼저 변경된 경우 덮어쓰지 않는다.

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/91a16501-1f7d-4f8c-b596-16cbf6dd60d2.jpg'
WHERE id = 'aa199406-ac9e-5208-a181-2d92dd98a5bc'
  AND recipe_id = '856651e1-552c-57bc-ad7b-af8c901674e7'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00468_1.png';

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/3c3808e9-6a5a-4252-8f6c-e1d81a6a341b.jpg'
WHERE id = 'fd9d3ade-9e52-58e1-b869-867291acead1'
  AND recipe_id = '856651e1-552c-57bc-ad7b-af8c901674e7'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00468_2.png';

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/e316fd50-7474-451b-bee1-161bc5b7b774.jpg'
WHERE id = '20e4f36b-4d5b-5890-8b45-1f9b78e08f4e'
  AND recipe_id = '856651e1-552c-57bc-ad7b-af8c901674e7'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00468_3.png';

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/70210dd1-c129-425c-9c58-969d78197ac9.jpg'
WHERE id = '8bc6222a-9b54-5342-b423-10f841a1c585'
  AND recipe_id = '856651e1-552c-57bc-ad7b-af8c901674e7'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00468_4.png';

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/da3abba8-f3cb-4cff-ab06-22d40feca94c.jpg'
WHERE id = 'e67a2489-6dd3-5922-b3ce-51b40b8100ba'
  AND recipe_id = '856651e1-552c-57bc-ad7b-af8c901674e7'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00468_5.png';

UPDATE recipe_steps
SET image_url = 'https://cookpilot-photos-167403240280.s3.ap-northeast-2.amazonaws.com/review-photos/5f1c82c6-d511-44b0-8387-7da47357f742.jpg'
WHERE id = '95a4c917-ffd9-5d47-9af3-51581d8a01bf'
  AND recipe_id = '856651e1-552c-57bc-ad7b-af8c901674e7'
  AND image_url = 'http://www.foodsafetykorea.go.kr/uploadimg/cook/20_00468_6.png';
