-- 문화권·용도 태그 부여 (일회성).
--
-- 판단 근거: 제목, 원문 요리종류(RCP_PAT2), 1인분 열량.
-- assigned_by='RULE' — 규칙이 유도했다(사람이 하나씩 고른 MANUAL 이 아니다).
-- 재실행해도 행이 늘지 않는다.

BEGIN;

INSERT INTO recipe_tags (recipe_id, tag_code, axis_code, assigned_by) VALUES
  ('856651e1-552c-57bc-ad7b-af8c901674e7', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- LA 갈비구이
  ('856651e1-552c-57bc-ad7b-af8c901674e7', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- LA 갈비구이
  ('a32d03c6-9e44-559f-a7eb-76fd96f74887', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 가지 탕수육
  ('9a1a49a6-01f7-5c85-8afa-73ba1371de99', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 가지겉절이
  ('cfeaaca2-b6f7-5649-bc59-843206faebbd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 가지나물냉국
  ('cfeaaca2-b6f7-5649-bc59-843206faebbd', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 가지나물냉국
  ('47a74cbe-2c49-5ecf-8963-664a59033538', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 가지라따뚜이
  ('47a74cbe-2c49-5ecf-8963-664a59033538', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 가지라따뚜이
  ('9507cbd5-1e78-5807-acb9-d43a4dc03058', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 가지말이샐러드
  ('9507cbd5-1e78-5807-acb9-d43a4dc03058', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 가지말이샐러드
  ('1fa07d9c-c55a-556a-be1b-1c7539bc003e', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 가지볶음
  ('0dda04c4-9311-599e-a2b3-e9ca5b6451e1', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 가지볶음밥
  ('1c53694d-33ce-59f6-a100-d5341a4a6fff', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 간장소스를 곁들인 새우전복찜
  ('dbb5cb6c-e677-50a4-9e8d-dca64cb7f442', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 간장아귀찜
  ('7e40a5d4-f021-5aa0-a08a-2f34a20eea8c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 간편조기찜
  ('10dccd2b-ca99-56a6-a4c5-ad2936c2d318', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 갈릭칩동태구이
  ('f4301311-0280-502f-8405-e237ac03bfc4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 갈치고추장구이
  ('c08c075d-f124-5bbe-ba9f-8689daafe5f8', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 감귤 김치
  ('c08c075d-f124-5bbe-ba9f-8689daafe5f8', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 감귤 김치
  ('897074fd-8d5c-54ec-9fff-3c48f25e8ef8', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 감닭떡갈비
  ('459dace4-afe7-5d9a-97d4-0e9e5df8ac53', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 감말랭이 찰빵
  ('a73ea942-e1da-5efa-a409-7b9585517b0e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 감자 주먹밥
  ('a73ea942-e1da-5efa-a409-7b9585517b0e', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 감자 주먹밥
  ('5eec1518-1e81-56ee-97ab-e961508042e3', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 감자 팬케익
  ('d9faa933-a761-5332-9de1-568f527f03e3', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 감자냉채
  ('796b17ce-2daf-5b8d-9449-b0e63f26c073', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 감자느타리버섯국
  ('fc335923-d5a3-5d8f-919a-a2c46dd84abb', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 감자를 곁들인 야채스튜
  ('cd0dd1c8-2a66-59f7-9a44-52536931e6eb', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 감자미역국
  ('cd0dd1c8-2a66-59f7-9a44-52536931e6eb', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 감자미역국
  ('8be860dd-8b24-57ee-8c90-754c5582059f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 감자주스
  ('11904c67-0e83-5bc5-aecb-de4ab09849e3', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 감태샐러드말이와 버섯말이깐풍
  ('a66bfa07-b837-579f-bfcb-0d9dbb05326f', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 강황 투움바 파스타
  ('7610f7e3-e34d-5b1e-85b2-55777c3ac71d', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 건강 마늘 꿀환
  ('3f79c692-0601-554c-8752-96afe049b4f7', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 건강가지말이+참깨마요소스
  ('15e9b70f-e43f-5ac7-8399-530baf1c96ee', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 건도토리묵버섯들깨국
  ('15e9b70f-e43f-5ac7-8399-530baf1c96ee', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 건도토리묵버섯들깨국
  ('20a8fbd7-1dfc-5ab2-9570-19f9a26a7513', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 검은깨라면크로켓
  ('20a8fbd7-1dfc-5ab2-9570-19f9a26a7513', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 검은깨라면크로켓
  ('20a8fbd7-1dfc-5ab2-9570-19f9a26a7513', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 검은깨라면크로켓
  ('1fa0e3db-7b37-5845-9467-882fd0bc8d20', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 검은콩 스프
  ('e4541cdf-27ca-5e67-80bc-bc7708c0c7f6', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 검은콩 크림빵
  ('4e0e0c75-5a11-52a6-b01e-ecdb6fe46fcc', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 검은콩보리된장찌개
  ('4e0e0c75-5a11-52a6-b01e-ecdb6fe46fcc', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 검은콩보리된장찌개
  ('24959482-8f11-5a46-9b47-3d503ccf6330', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 검은콩부추전
  ('1a1dd651-685c-5d8a-a4ca-118777dabb57', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 검은콩피시볼조림
  ('defad3ff-39ed-54c1-938d-639576ccc8fb', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 겨자 오이고추소박이
  ('6d2b11d0-711c-5fa4-a301-6b002d1893f1', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 겨자아욱쌈밥
  ('289d7a8e-1a68-5c46-b418-be253e6793cb', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 견과류 미숫가루 빵
  ('dd5817df-ec59-5157-b03b-1109ad36c273', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 견과류들깨해장탕
  ('dd5817df-ec59-5157-b03b-1109ad36c273', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 견과류들깨해장탕
  ('166e411a-5a1e-5c85-8e11-4fdc8fd807cf', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 견과류문어떡갈비
  ('ff47b029-2105-5e45-bc8e-edaedf1b8440', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 견과류통삼겹살찜
  ('10000000-0000-0000-0000-000000000005', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 계란볶음밥
  ('342e0946-4d2a-597a-8124-d17d261c9ea3', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 계란숙샐러드
  ('67a491bc-f5ca-523e-9577-be501bb1d9ed', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 고구마 경단
  ('67a491bc-f5ca-523e-9577-be501bb1d9ed', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 고구마 경단
  ('83e3ce0e-e374-52f8-9d1f-f5acf4ea9ab0', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 고구마 공갈빵
  ('5e4e1969-f2cb-53b5-9256-87294ef5365c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 고구마 김치
  ('5e4e1969-f2cb-53b5-9256-87294ef5365c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 고구마 김치
  ('df1d3626-96f6-5c9f-a162-20a6f92c40d9', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 고구마 바나나 무스
  ('df1d3626-96f6-5c9f-a162-20a6f92c40d9', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 고구마 바나나 무스
  ('de90f1d3-6378-50e0-9f46-d2fee5cfdc1c', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 고구마 찰 빵
  ('9d815bc3-19bc-5454-9235-3e2697943235', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 고구마두부스테이크
  ('0b7080a5-7072-515c-b166-9c1047caa247', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 고구마라떼
  ('0b7080a5-7072-515c-b166-9c1047caa247', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 고구마라떼
  ('6e15b6e8-5840-576b-97c2-c1ae71a7efe7', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 고구마란
  ('8b4ea8fc-c72d-539c-916b-0588585ed20f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 고구마빵&과일잼
  ('872817f3-73d1-53d6-8eac-0360f9d18cd0', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 고구마순나물
  ('872817f3-73d1-53d6-8eac-0360f9d18cd0', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 고구마순나물
  ('76bed10c-8e5c-5eab-bd94-7619a9157e2b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 고구마순들깨나물
  ('41e2a171-5988-55ac-ac70-b0b15445d3dd', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 고구마치즈크로켓
  ('41e2a171-5988-55ac-ac70-b0b15445d3dd', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 고구마치즈크로켓
  ('ceab2fc1-4f82-5c55-b9b6-1c3fe3713818', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 고기완두조림
  ('0d8822aa-2aca-55a6-9150-e4527f5b5d9a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 고등어 강정
  ('0d8822aa-2aca-55a6-9150-e4527f5b5d9a', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 고등어 강정
  ('0d8822aa-2aca-55a6-9150-e4527f5b5d9a', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 고등어 강정
  ('e9bb83ca-a014-5ba4-9911-19f6910734de', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 고등어 라따뚜이 파스타
  ('6081768f-55dd-50a7-8727-2257526fc865', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 고등어 묵말랭이 냉소바
  ('7cbfbc88-951f-5d64-bc9f-15ef61dc62a8', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 고등어된장구이
  ('56c2e599-901e-56b6-bd3a-f4723fc2dcf1', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 고등어카레탕수
  ('b24149a6-44da-5f09-aa47-2fc916f44b88', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 고추김치
  ('b24149a6-44da-5f09-aa47-2fc916f44b88', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 고추김치
  ('d65c84d0-dd1e-5235-9725-bfed9243b65f', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 고추잡채
  ('d65c84d0-dd1e-5235-9725-bfed9243b65f', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 고추잡채
  ('d65c84d0-dd1e-5235-9725-bfed9243b65f', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 고추잡채
  ('14e961b1-0de9-59be-bab1-1ccf6d72bcbc', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 고추장 가스파초 물쫄면
  ('6dc60ade-60a5-52b5-95bc-14a1defdc42f', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 고추장 라따뚜이
  ('6dc60ade-60a5-52b5-95bc-14a1defdc42f', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 고추장 라따뚜이
  ('d6e055d2-166a-519c-b0fd-f46389e1581d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 고추장카르네와 K-쌈밥
  ('0ce4a1a4-fd92-5946-a4af-f917f55944e2', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 곤드레 밥버거
  ('0ce4a1a4-fd92-5946-a4af-f917f55944e2', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 곤드레 밥버거
  ('58d4792b-9a6f-5ba6-be35-13d1661665b0', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 곤드레야 순두부 아란치니
  ('03a28d85-0ba8-5da4-8318-5caac304e256', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 곤약 감자조림
  ('03a28d85-0ba8-5da4-8318-5caac304e256', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 곤약 감자조림
  ('aa2b7d59-9137-5307-b45c-75d1e0ed7b5a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 곤약 백김치 말이
  ('aa2b7d59-9137-5307-b45c-75d1e0ed7b5a', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 곤약 백김치 말이
  ('22b22a72-641a-52d8-bbac-e309f7dfc40f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 곤약 콩조림
  ('22b22a72-641a-52d8-bbac-e309f7dfc40f', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 곤약 콩조림
  ('ecdae938-5e0e-528f-83c6-18bb04938c6a', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 곤약냉면
  ('8937f832-c316-52e4-ad10-5388faaf0b1f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 곤약잡채
  ('8937f832-c316-52e4-ad10-5388faaf0b1f', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 곤약잡채
  ('8937f832-c316-52e4-ad10-5388faaf0b1f', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 곤약잡채
  ('8937f832-c316-52e4-ad10-5388faaf0b1f', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 곤약잡채
  ('23ba9f39-2e36-52d3-a489-ef8a395ef477', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 곤약함박스테이크
  ('b14d894b-0021-50fc-a3b9-58499e4e27d6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 골뱅이과일무침
  ('b14d894b-0021-50fc-a3b9-58499e4e27d6', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 골뱅이과일무침
  ('b36506ee-ecbf-5db7-a8c2-ca88663d5914', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 골뱅이무침
  ('b36506ee-ecbf-5db7-a8c2-ca88663d5914', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 골뱅이무침
  ('5660bf81-e5b6-512f-b07b-071019d14b42', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 골뱅이무침과 삼겹살수육
  ('5660bf81-e5b6-512f-b07b-071019d14b42', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 골뱅이무침과 삼겹살수육
  ('bf677354-2a7f-5736-83f2-3c5fa03cdaf1', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 곶감 베이글
  ('bf677354-2a7f-5736-83f2-3c5fa03cdaf1', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 곶감 베이글
  ('bd28c00f-574d-59d3-bcde-4cdfbc263fa9', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 과일 김치
  ('bd28c00f-574d-59d3-bcde-4cdfbc263fa9', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 과일 김치
  ('defb7fe5-bc6a-5a0d-8fb2-0e81df87bf35', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 과일 먹은 닭탕수
  ('0a09801d-0669-5e73-b7dc-f8fdf7a64533', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 과일 샐러드 또띠아
  ('0a09801d-0669-5e73-b7dc-f8fdf7a64533', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 과일 샐러드 또띠아
  ('68d0b07f-af73-52c7-8a97-fe1bf4a2bc0d', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 과일 요구르트 샐러드
  ('6cbed6a2-51b5-53b7-b692-feb9c277ed43', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 과일 젤리
  ('6cbed6a2-51b5-53b7-b692-feb9c277ed43', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 과일 젤리
  ('8ea9ecb6-f907-5970-95e5-048d31241c59', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 과일 주스 조림
  ('8ea9ecb6-f907-5970-95e5-048d31241c59', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 과일 주스 조림
  ('608641fa-bfb0-5c79-905e-e32aa737855a', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 과일 파이
  ('608641fa-bfb0-5c79-905e-e32aa737855a', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 과일 파이
  ('fb4833cf-6e20-56c6-a17d-7f223da3ff42', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 과일 피클 젤리
  ('fb4833cf-6e20-56c6-a17d-7f223da3ff42', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 과일 피클 젤리
  ('41133cf4-371b-5448-9e36-823379baa2d8', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 과일갈비찜
  ('41133cf4-371b-5448-9e36-823379baa2d8', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 과일갈비찜
  ('782cbb95-27ae-5f2f-aa52-58a973fc9132', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 과일겉절이
  ('36e5b397-4292-5e08-8163-77549f35477e', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 과일무스 테린
  ('36e5b397-4292-5e08-8163-77549f35477e', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 과일무스 테린
  ('27ecc76e-471e-5982-8bb8-645b3a4cd79c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 과일삼겹살조림&파채무침
  ('86601825-31ba-5d27-8a31-a16e8b22e9d0', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 과일퓨레수제함박스테이크
  ('59c89317-1b2e-5292-8e4a-337188ed7bb6', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 과카몰리 갈치구이
  ('3460e8bb-e3e8-5b38-a349-e5c884c543e0', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 관자브로콜리스프
  ('93c9177a-eb68-5845-a612-e7c44b47d7f3', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 관자해장국
  ('93c9177a-eb68-5845-a612-e7c44b47d7f3', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 관자해장국
  ('af4ea2e4-5e1e-5a8f-8b5d-1b96df6b20a8', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 광어스테이크
  ('e469073d-602a-58e0-90ed-4a311e7292c5', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 구기자모듬장조림
  ('e469073d-602a-58e0-90ed-4a311e7292c5', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 구기자모듬장조림
  ('590caec7-14a7-5895-acb5-e89a73889a4c', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 구운 바나나
  ('2a835a97-7580-5aa0-8509-6f4326041a7d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 구운 주먹밥
  ('2a835a97-7580-5aa0-8509-6f4326041a7d', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 구운 주먹밥
  ('5b59e748-7179-5d0d-8518-33ed479a44a8', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 군고구마 라떼
  ('5b59e748-7179-5d0d-8518-33ed479a44a8', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 군고구마 라떼
  ('ab0f812b-08fe-5132-8562-327758b56cc5', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 굴 두부 국
  ('c74fbdb3-42ad-59d6-9836-3088a42c776e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 굴림만두된장국
  ('9e552975-e13c-5fa5-a98b-d5a97aefd718', 'CUISINE_ASIAN', 'CUISINE', 'RULE'),  -- 그린커리
  ('3dc62ab1-19e3-5271-84b0-62b0bb1f4d2a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 근대쌈밥과 멸치견과류쌈장
  ('d96c2590-9903-5d85-b378-c51b340a4e03', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 근채류주먹밥
  ('d96c2590-9903-5d85-b378-c51b340a4e03', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 근채류주먹밥
  ('ef34ac75-e816-5c44-bb76-31d39439f443', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 금태찜
  ('ef34ac75-e816-5c44-bb76-31d39439f443', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 금태찜
  ('4dc847e0-94a4-55a1-819b-730c0fe20fe9', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 김말이두부스테이크
  ('c834b289-ac6b-582e-a80d-e10384729bda', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 김자반
  ('c834b289-ac6b-582e-a80d-e10384729bda', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 김자반
  ('fd77399f-d0db-5102-8f37-fd3342bc6708', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 김치 고구마밥
  ('41217f61-ce94-5910-a18f-bfc5c7656d5a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 김치떡
  ('41217f61-ce94-5910-a18f-bfc5c7656d5a', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 김치떡
  ('7b793836-04ee-5298-88d8-1ebb4a0f2ca2', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 김치밥그라탕
  ('10000000-0000-0000-0000-000000000002', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 김치볶음밥
  ('17318ab9-2f21-52cc-9015-40f75875d070', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 김치양배추볶음
  ('17318ab9-2f21-52cc-9015-40f75875d070', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 김치양배추볶음
  ('41d98179-d00e-55b6-a98c-a3fdbfc8eb1b', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 까르보나라뇨끼
  ('461ddcfc-d5f0-54a8-8dc7-d83a5779260b', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 깐풍주먹밥
  ('461ddcfc-d5f0-54a8-8dc7-d83a5779260b', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 깐풍주먹밥
  ('4872816c-b410-55f4-a480-7f498d88bd64', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 깐풍파스타
  ('6536d9b2-6771-56d6-b398-a8a5002bea39', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 깻잎 쌈두부 라자냐
  ('5ced5a33-2a4c-5fdf-b36e-c459ef33f78d', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 깻잎단호박튀김
  ('098aea8c-0288-5dd9-beb1-fbcfa6d19bc9', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 깻잎애호박찜
  ('098aea8c-0288-5dd9-beb1-fbcfa6d19bc9', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 깻잎애호박찜
  ('a662b510-c15c-568d-821c-f5bd7c59fdce', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 깻잎장아찌 롤
  ('e75b682c-e4ee-54db-b80f-26b97c33995e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 꼬막 달래 된장 무침
  ('e75b682c-e4ee-54db-b80f-26b97c33995e', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 꼬막 달래 된장 무침
  ('8b132617-4851-5f0e-a7d3-532271ade58b', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 꼬막떡꼬치구이
  ('a21c32ea-61a6-5118-b16b-041d66e71b4e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 꽁치채소말이
  ('e30204a9-23cd-5294-a1fb-b8de9418472f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 꽃게강정
  ('e30204a9-23cd-5294-a1fb-b8de9418472f', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 꽃게강정
  ('e30204a9-23cd-5294-a1fb-b8de9418472f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 꽃게강정
  ('c7cafeae-1891-5f05-8c49-38762210be50', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 꽃게바지락찌개
  ('c7cafeae-1891-5f05-8c49-38762210be50', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 꽃게바지락찌개
  ('ed4ceee2-8415-5454-9824-6ab2cd61a38a', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 꽃설기
  ('425580c1-edf2-5b65-bfd7-11fc713dbc06', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 꽈리고추 닭강정
  ('425580c1-edf2-5b65-bfd7-11fc713dbc06', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 꽈리고추 닭강정
  ('425580c1-edf2-5b65-bfd7-11fc713dbc06', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 꽈리고추 닭강정
  ('35af2f68-3cb8-5402-b966-c583a7e9ef43', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 나가사키부대찌개
  ('ed17ada3-5aad-5b44-ba49-1bb28dfe4820', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 나박김치
  ('ed17ada3-5aad-5b44-ba49-1bb28dfe4820', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 나박김치
  ('ed17ada3-5aad-5b44-ba49-1bb28dfe4820', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 나박김치
  ('20285003-bb16-55c5-99a6-4d36c3d764f7', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 나스와 샐러드
  ('b3ca3c94-eee3-5d06-80c6-fa830aa846c2', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 낙전새 카레볶음밥
  ('1a288f82-385a-5fb5-94d1-705e7ba951e0', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 낙지강회
  ('6cdf02d7-0013-5f11-b352-91bf25f95ec4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 낙지모듬초말이
  ('6cdf02d7-0013-5f11-b352-91bf25f95ec4', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 낙지모듬초말이
  ('5f3af4fb-3459-50ca-bfd0-ea41c37e1f5b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 낙지묵은지콩나물국
  ('5f3af4fb-3459-50ca-bfd0-ea41c37e1f5b', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 낙지묵은지콩나물국
  ('5f3af4fb-3459-50ca-bfd0-ea41c37e1f5b', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 낙지묵은지콩나물국
  ('906bef5b-167f-5d73-af27-38e872508a77', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 낙지브로콜리볶음
  ('0364ce6c-d778-5d28-b5f8-bd50363b1980', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 낫토 시래기 라이스전
  ('0364ce6c-d778-5d28-b5f8-bd50363b1980', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 낫토 시래기 라이스전
  ('5e7deaf3-30ec-56c6-a009-99a09ceb7597', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 냉머위들깨나물
  ('5e7deaf3-30ec-56c6-a009-99a09ceb7597', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 냉머위들깨나물
  ('6679bbf1-7944-5438-b7a5-69ce0ef7814a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 냉잡채
  ('6679bbf1-7944-5438-b7a5-69ce0ef7814a', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 냉잡채
  ('6679bbf1-7944-5438-b7a5-69ce0ef7814a', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 냉잡채
  ('6460552d-fe9a-5c46-975f-67fa0ebd2607', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 냉토마토파스타
  ('059d06a3-df04-56d4-8345-a6a589e57a38', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 냉파스타
  ('3ab318c1-23df-5de4-8a1b-7556e5a8903f', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 녹차 파나코타
  ('3ab318c1-23df-5de4-8a1b-7556e5a8903f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 녹차 파나코타
  ('fb681121-0134-50b3-87e8-cc33a8648940', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 녹차귀리라떼
  ('fb681121-0134-50b3-87e8-cc33a8648940', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 녹차귀리라떼
  ('3ae9eb2b-16e1-5371-b3a9-ee0ce271d067', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 누룽지 요거트 파르페
  ('3ae9eb2b-16e1-5371-b3a9-ee0ce271d067', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 누룽지 요거트 파르페
  ('a1673ebb-5aec-5d97-bfb0-075e6abeea12', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 누룽지 피자
  ('a1673ebb-5aec-5d97-bfb0-075e6abeea12', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 누룽지 피자
  ('5a1096d3-6f6f-5c13-8d24-6397ee816dc9', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 누룽지과자
  ('2ca26fd6-6a57-548d-87ec-b19796841aaa', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 누룽지새우튀김
  ('e815bba6-3074-5c07-9517-42fe501d864e', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 느타리버섯볶음
  ('e851139c-cafd-5c3f-8319-8d039aa307f5', 'CUISINE_ASIAN', 'CUISINE', 'RULE'),  -- 니고랭
  ('6a93b7d6-fe1c-531c-a4b5-d5c6dedebfd5', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 다시마전
  ('433c4ebf-674c-5a39-be97-fe8ffb627392', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 다시마칩
  ('d2658a1a-8313-59fd-91a1-839fcd6e3024', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 다이어트국수
  ('09d90137-5f82-5d97-8e09-75f2dbd50932', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단구마 무스 샌드 케이크
  ('09d90137-5f82-5d97-8e09-75f2dbd50932', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 단구마 무스 샌드 케이크
  ('6f3f9e3f-18cf-564c-8c24-4102a4a50810', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박 경단
  ('6f3f9e3f-18cf-564c-8c24-4102a4a50810', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 단호박 경단
  ('1e0fddc4-5ffc-59a3-b61c-d57417837b5c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 단호박 된장매쉬와 해물굴림만두
  ('1e0fddc4-5ffc-59a3-b61c-d57417837b5c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 단호박 된장매쉬와 해물굴림만두
  ('1077e34e-fbce-5a1e-829e-8414cb5de0c5', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박 두부 포타주
  ('1077e34e-fbce-5a1e-829e-8414cb5de0c5', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 단호박 두부 포타주
  ('e6ce15ae-16c1-5e4d-ae8a-1b59f99f377a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 단호박 두부찜
  ('e6ce15ae-16c1-5e4d-ae8a-1b59f99f377a', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 단호박 두부찜
  ('b9016d90-35d3-58b8-83b2-c0be12bb3af4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 단호박 새우찜
  ('91a5ff2f-580f-574d-9881-59547b3e91bf', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 단호박 생선탕수, 키위소스
  ('a16b2b95-4ef8-5e91-98ce-0d2b015327e2', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박 스프
  ('446d938f-ee96-5006-9dad-299d2128a79c', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박 양파잼 샌드위치
  ('be818fcd-4311-5bcf-837f-e41d660ca908', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 단호박 호밀빵
  ('74253fe6-c3e0-5197-9194-11db16de75e7', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박견과류샐러드
  ('6032cc62-76e0-5479-8b3c-37acde2b19ba', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 단호박닭꼬치
  ('e0e70375-0f38-5308-adaf-5a96dda6c59a', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박두부팬케이크
  ('e0e70375-0f38-5308-adaf-5a96dda6c59a', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 단호박두부팬케이크
  ('b238c217-1b31-5939-97ea-55e2aa93a5c6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 단호박떡갈비
  ('f3776504-54b6-55c6-b192-a2e1fe038612', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 단호박떡볶이
  ('f3776504-54b6-55c6-b192-a2e1fe038612', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 단호박떡볶이
  ('f3776504-54b6-55c6-b192-a2e1fe038612', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 단호박떡볶이
  ('5cd5e565-8c0d-5b85-836f-912bc8f75420', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박리조또
  ('56fc1645-41fe-5e82-be4e-4ea755671a29', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 단호박몽블랑
  ('8ade9431-8783-5337-9c06-dbdbbeda066b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 단호박배추된장국
  ('8ade9431-8783-5337-9c06-dbdbbeda066b', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 단호박배추된장국
  ('2496fa96-1798-5ae5-9ffa-8f642873a1d0', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 단호박약식
  ('2496fa96-1798-5ae5-9ffa-8f642873a1d0', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 단호박약식
  ('a678a6aa-6eb8-57db-a1b8-6ffa4f38be4b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 단호박제육볶음
  ('45b12114-479d-54fa-a4f7-b5997b826508', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 단호박치즈스틱
  ('a5801274-d241-5a4b-8d0c-13197630d655', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박크림을 곁들인 브로콜리 베이글
  ('a5801274-d241-5a4b-8d0c-13197630d655', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 단호박크림을 곁들인 브로콜리 베이글
  ('e1cf7b5a-f7a4-582b-954c-9fdcafd1cc27', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박크림파스타
  ('9b6c5728-f9fc-5f95-801e-e3dc490d03bd', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박푸딩
  ('9b6c5728-f9fc-5f95-801e-e3dc490d03bd', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 단호박푸딩
  ('31625557-8e3b-53f6-9b37-c3d8368e40dc', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박피자
  ('cf66c278-fdef-59cb-9dd7-7979a4ab82d9', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 단호박함박스테이크
  ('17d3a5ed-9db1-5006-8254-d43538dc9bd3', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 달래두부김치무침
  ('17d3a5ed-9db1-5006-8254-d43538dc9bd3', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 달래두부김치무침
  ('ba57193f-2af7-5f7f-8fc9-e0bda31b7c10', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 달래바싹불고기
  ('21fa25c2-c8f8-5e6d-9957-7bf6d8528410', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 달코미 고구마 타르트
  ('68b61571-bdaf-523d-a15a-f3d8fe5274a5', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 달콤과일깍두기
  ('68b61571-bdaf-523d-a15a-f3d8fe5274a5', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 달콤과일깍두기
  ('b142d4f6-de4d-571a-bd6f-5fd852e70163', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 달콤스테이크
  ('d8cd3c34-94b0-5b6b-99c7-0b4c0efe327f', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 닭가슴살 두부선
  ('9372b8ce-337f-5db3-adc6-ec37ff2a7f1b', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 닭가슴살 스테이크
  ('84761bac-c4d0-5109-a8c8-ce6ecfcc644e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭가슴살 채소쌈
  ('b3b6ce36-4452-5935-ba58-b0892720a603', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 닭가슴살리조또
  ('21783a23-2f23-5fbf-b455-86a2f9c1ec66', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭가슴살말이
  ('e47d02c8-749e-557e-842b-2e2766ef9ea5', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 닭가슴살샌드스테이크
  ('c962aaaf-4ea9-50a6-b1d6-4d3fe1f895d7', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭가슴살청포묵비빔밥
  ('3c25c808-7f1d-5ab7-abc1-e7c5f493a400', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 닭가슴살현미스테이크
  ('f11fabe4-903b-5190-a606-0e3822d2a77f', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 닭가슴살호두크로켓
  ('10000000-0000-0000-0000-000000000008', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭갈비
  ('9effc579-e6ac-5d1b-8bd3-25f6ced6d8a1', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭갈비볶음면
  ('188d1718-1817-5a5d-b680-9d8797458097', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭강정
  ('188d1718-1817-5a5d-b680-9d8797458097', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 닭강정
  ('188d1718-1817-5a5d-b680-9d8797458097', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 닭강정
  ('bd5a3b43-4f33-59ff-8601-4efdf7974635', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭고기 완자삼계죽
  ('2661f97b-8715-5fd5-8b6a-909016601278', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 닭고기 찰깨빵
  ('ed9e3070-3c74-55a7-80f0-ae8e7133340d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭고기김치찌개
  ('e8571fdb-7783-5b07-9634-ddbbf6c05563', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 닭고기또띠아
  ('6cad6119-0a12-5e7f-bbce-ece996116b94', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭고기볶음밥
  ('39ffd775-c9f2-5afe-b4f6-9e30d0b056e7', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 닭고기스테이크
  ('f896578b-095b-5628-98af-8806785e0b63', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 닭고기월남쌈
  ('06d2eb51-528f-5e7c-9f93-a6843b42f9c2', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 닭고기채소스파게티
  ('f6140eec-5368-52fa-9cf2-f405f4b29b70', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 닭고기파스타
  ('e4415e58-f294-57d1-a904-e6ea749f1c7f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭곰탕
  ('e4415e58-f294-57d1-a904-e6ea749f1c7f', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 닭곰탕
  ('124e45a4-32b0-5f4f-91f2-75f33b844ef4', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 닭날개튀김
  ('35b5bac1-9b09-5479-8fc6-7040ed754762', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 닭봉주먹밥
  ('35b5bac1-9b09-5479-8fc6-7040ed754762', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 닭봉주먹밥
  ('4d9f9e66-b4de-5833-8c39-e21df5445fe9', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 담양식떡갈비&야채쌈
  ('52c38fd3-dd4e-5ebf-8527-d0e654c99a10', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 당 떨어진 마들렌
  ('b2bc1e06-ce5d-5cba-a5e3-b0004185ea41', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 당근&그릭요거트 케이크
  ('b2bc1e06-ce5d-5cba-a5e3-b0004185ea41', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 당근&그릭요거트 케이크
  ('433300e3-44b5-5654-b562-8f33547e34cc', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 당근새우 카나페
  ('8b67f62f-8203-5765-af3d-7663d210d62d', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 대구지리탕
  ('60196087-c142-58f9-a72a-248f61e55274', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 대추닭살리조또
  ('d6a3fe0a-2c5d-546f-b426-fd41826b6d4f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 대하마늘볶음밥
  ('874f43c9-6a82-550a-bf5d-bc3bc2b3b841', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 대하조림
  ('18496400-f4e5-5818-9dbb-1e457d1ac169', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 더덕비트물김치
  ('18496400-f4e5-5818-9dbb-1e457d1ac169', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 더덕비트물김치
  ('4d5e96d0-24c2-5c19-81e7-c68d93b6b944', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 더덕얼갈이겉절이
  ('4d5e96d0-24c2-5c19-81e7-c68d93b6b944', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 더덕얼갈이겉절이
  ('fec8c2d7-31d4-5899-8282-e681ce53d4b0', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 도라지 검은깨 튀김
  ('fec8c2d7-31d4-5899-8282-e681ce53d4b0', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 도라지 검은깨 튀김
  ('d8865cb6-f92c-5c8b-a55e-18a07f43300b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 도토리묵 콩국
  ('d8865cb6-f92c-5c8b-a55e-18a07f43300b', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 도토리묵 콩국
  ('3d937353-c746-577c-b1b5-3e8d5454e1ba', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 돈불고기 파인애플볶음
  ('c3c1c27b-2bac-58e3-bcd0-cb8a32f045d7', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 돌나물 샐러드
  ('a1b64e2c-2e6d-5021-a1f2-7f0e2284e9f6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 돼지갈비 볶음
  ('44858bc7-b859-5539-a0e1-207fd0f30d0c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 돼지갈비구이와 백김치와인조림
  ('44858bc7-b859-5539-a0e1-207fd0f30d0c', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 돼지갈비구이와 백김치와인조림
  ('756e19e3-5590-5e56-85e2-b7d2c92537cd', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 돼지고기 포두부 파스타
  ('b1c9407a-503c-57c1-8587-682c4d41e309', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 돼지고기말이튀김
  ('b1c9407a-503c-57c1-8587-682c4d41e309', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 돼지고기말이튀김
  ('99a27e2a-e96a-5056-9c1d-f26bbdaa6114', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 돼지머리수육맑은전골
  ('99a27e2a-e96a-5056-9c1d-f26bbdaa6114', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 돼지머리수육맑은전골
  ('1fa1ec71-8690-5b50-9794-2083bcdf6786', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 돼지수육맑은전골
  ('1fa1ec71-8690-5b50-9794-2083bcdf6786', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 돼지수육맑은전골
  ('95781dbc-aac3-585b-a485-11d5a6aa8b0c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 된장 두부찌개
  ('95781dbc-aac3-585b-a485-11d5a6aa8b0c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 된장 두부찌개
  ('8223e643-3220-5a9b-8442-0b0d5201cfd9', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 된장국
  ('8223e643-3220-5a9b-8442-0b0d5201cfd9', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 된장국
  ('4284fd0e-9660-51e2-aa8b-595e9fc8aa73', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 된장냉국
  ('4284fd0e-9660-51e2-aa8b-595e9fc8aa73', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 된장냉국
  ('821c4886-162d-55cb-9c75-a877820abbc3', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 된장숙성저수분수육
  ('1dfd99b7-f013-5e4b-825d-3d702f12db6d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 된장시금치옹심이
  ('10000000-0000-0000-0000-000000000004', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 된장찌개
  ('24b52c9a-ce39-5148-896d-8741124b9eb9', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 된장크림소스 잡곡 오므라이스
  ('2c2646fa-97c2-5b60-8053-c2d61861434f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두가지묵샐러드
  ('2c2646fa-97c2-5b60-8053-c2d61861434f', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 두가지묵샐러드
  ('679e50c8-ad8c-5b2d-9486-61549936617c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두릅부추 소고기말이
  ('2c6ce0e7-d236-5f6d-8f1b-4f299f5b3387', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 두부 깻잎 과자
  ('c53c8f65-484e-5c05-8560-f0a0c5199b14', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두부 비빔호박쌈
  ('46f49dfe-665f-5bf4-8f51-e9055960718b', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 두부 샌드위치
  ('2e79f9d9-5101-5058-81ac-36cf1ea67c9a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두부 채소 볶음밥
  ('2d972f3e-d780-5352-8022-243133a67f7a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두부 튀김 조림
  ('2d972f3e-d780-5352-8022-243133a67f7a', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 두부 튀김 조림
  ('05a822a3-6a36-59af-b94f-eba7cd9c6d5d', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 두부 해산물 꼬치구이
  ('8d3a7b49-9732-5093-b694-a3c9e018082f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두부곤약조림
  ('950e4c1d-198d-5e6a-b052-77e57e57ecd5', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 두부곤약파스타
  ('8938fe8c-4b88-5d70-81ff-44ad6f2b6492', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 두부구이, 버섯소스
  ('2ec15f70-22cd-5177-a6ab-ea93febbe41a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두부된장무침
  ('a4fbfeca-8bca-5a08-bd48-c393f4f2372d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두부말이찜
  ('4e79eb4d-900e-5902-9590-074c72432a5c', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 두부빼빼로
  ('a0156f61-ecfb-5b67-8ee9-391ad0c77277', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두부샐러드 메밀김밥
  ('a0156f61-ecfb-5b67-8ee9-391ad0c77277', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 두부샐러드 메밀김밥
  ('1f4941ac-c0db-534a-aa57-f4b9662a8a2b', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 두부스테이크
  ('a271b86b-1cc9-508d-995c-888cd109ade0', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 두부양갱
  ('bcf2fcec-1218-5daf-b1bc-f2f0ddb10744', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 두부있슈
  ('10000000-0000-0000-0000-000000000003', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두부조림
  ('f0412b95-7245-59ea-abfb-83e5825eff97', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두부청국장죽
  ('6130fa05-36b8-5f12-812f-cc1ab6b73662', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 두부튀김&무소스
  ('9d185c96-feb6-54d8-9bec-5b61447ff668', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 두부티라미수
  ('00f4c7d4-9c55-5efc-ae1a-5d405e809618', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 두유 라이크 파스타
  ('e27f507a-34cc-5cfb-b61b-235339f89630', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 두유 마 떡갈비
  ('107cdce1-b7ea-5a97-9147-fea425e8f601', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 둥지 튀김
  ('94fca5c5-244e-5206-a8b1-95e436c34604', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 들깨 삼계면
  ('91f712c9-9ce7-5fef-804e-f0959472d8be', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 들깨된장육개장
  ('6291979e-023d-5ec7-aba6-ca1d8aa88265', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 들깨삼겹살과 참외쌈장
  ('2809e804-b5b2-5c8e-acab-2d39805e21af', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 들깨순두부찌개
  ('c71ca1e4-a2d4-575b-94ee-1e3c23a7705c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 등심 배구이
  ('b2a1fc7d-2a45-5407-a104-856d0322b847', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 딸기소스닭가슴살채소무침
  ('b5022d9b-67f7-54b9-9cff-056f1182f2d8', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 딸기연두부쉐이크
  ('8316f639-b4d1-5be3-bff2-e54dcca74596', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 땅콩 밀푀유 샌드
  ('8316f639-b4d1-5be3-bff2-e54dcca74596', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 땅콩 밀푀유 샌드
  ('320bd565-36bb-56ae-8e6c-9473ba98e219', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 땅콩소스 버섯가지나물
  ('320bd565-36bb-56ae-8e6c-9473ba98e219', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 땅콩소스 버섯가지나물
  ('320bd565-36bb-56ae-8e6c-9473ba98e219', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 땅콩소스 버섯가지나물
  ('395739d2-cb1e-522a-b1bd-4e273153d390', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 땅콩호박 타르트
  ('395739d2-cb1e-522a-b1bd-4e273153d390', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 땅콩호박 타르트
  ('0f339f8f-2bfb-5cc7-a15d-53a172b5567a', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 떠먹는 송편
  ('d7e82014-8389-5463-889c-8cb1ae0976b6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 떡갈비와 미니잡곡밥
  ('69583c57-8b57-5ae8-bb4f-f615c8c1397c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 떡갈비주먹밥
  ('69583c57-8b57-5ae8-bb4f-f615c8c1397c', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 떡갈비주먹밥
  ('495cad53-dae6-59c4-a91d-b9f7da76b6d6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 떡갈비찜
  ('495cad53-dae6-59c4-a91d-b9f7da76b6d6', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 떡갈비찜
  ('3d3e38c3-653f-56ac-9fd0-c3a185c69fae', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 떡갈비콩나물밥
  ('354aef2e-46f6-52a7-a738-053b791b2cd5', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 떡갈비통치미국수
  ('d751012d-c67f-5975-a17e-55855722d158', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 떡꼬치구이
  ('8ab99915-86aa-517a-9fe3-3f4109f4db82', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 떡순이 두부쌈
  ('a930f032-4f17-53fc-8160-b7acdf729e8e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 떡완자조림
  ('42a033b8-27a5-5c20-b552-a5d07417ff36', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 라따뚜이
  ('42a033b8-27a5-5c20-b552-a5d07417ff36', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 라따뚜이
  ('10000000-0000-0000-0000-000000000001', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 라면
  ('be326a4f-f327-5fb5-bbfc-5b7919f9f67a', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 라면월남쌈튀김
  ('be326a4f-f327-5fb5-bbfc-5b7919f9f67a', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 라면월남쌈튀김
  ('be326a4f-f327-5fb5-bbfc-5b7919f9f67a', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 라면월남쌈튀김
  ('b3034ad1-3ee6-5ca6-a5d0-e31c2135442f', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 라이스버거떡갈비
  ('d41cf590-0529-574c-b811-403e2ecbb8d3', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 레몬 들깨 소스를 품은 떡꼬치
  ('d41cf590-0529-574c-b811-403e2ecbb8d3', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 레몬 들깨 소스를 품은 떡꼬치
  ('ba0dc93c-0893-5267-9ead-259a29925acf', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 레몬, 파슬리 빵가루를 입힌 도미
  ('ba0dc93c-0893-5267-9ead-259a29925acf', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 레몬, 파슬리 빵가루를 입힌 도미
  ('cab766b8-1743-58d8-a1ad-622ed331f508', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 레몬등갈비구이
  ('cab766b8-1743-58d8-a1ad-622ed331f508', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 레몬등갈비구이
  ('c12f421e-908f-551f-bad7-6053d7f4b3d1', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 레몬크림콩다식
  ('26406c24-9b0a-56c6-8284-41e89942e0db', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 렌틸콩 두유
  ('01a5e7f6-1a6e-55a9-9311-e68cad8be1d6', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 렌틸콩 크레이프
  ('a8a8e22c-5f6c-5a2e-9f81-fce6ed0a8c21', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 로제소스라면
  ('f2624ccb-c6ea-5a8c-a6b9-c1dcd6ffc964', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 로제파스타
  ('f7a7969a-d816-59cd-8bd0-d2baea5ca13d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 롤 삼계탕
  ('4bbfe594-c3ad-582a-a634-70dd5948ef47', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 롤피자
  ('4bbfe594-c3ad-582a-a634-70dd5948ef47', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 롤피자
  ('7bb720f1-743b-563d-9239-a9b051d5ba38', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 리코타치즈 카프레제
  ('a9d46f50-7e37-5f49-89e0-d4c0baeb33d3', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 마늘 불고기 덮밥
  ('c2e6c876-8ec4-5c49-a5d4-d0cbe9ba4e2d', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 마늘칩 감자샐러드
  ('c2e6c876-8ec4-5c49-a5d4-d0cbe9ba4e2d', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 마늘칩 감자샐러드
  ('af25337e-deb4-5cc5-a669-8120ab7505ed', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 마들깨미역국
  ('cdd16445-748b-54d3-aa8c-0fcf81ff2f8a', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 마마무스프
  ('cdd16445-748b-54d3-aa8c-0fcf81ff2f8a', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 마마무스프
  ('8ee2c47c-2532-594a-a4c9-09153e1eaa90', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 만가닥버섯볶음
  ('46f44e5d-c23f-5b83-a055-1a035998eaba', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 맑은부대찌개
  ('ec82f0d2-81d0-5004-8e32-0cf8fb934773', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 맑은육개장
  ('ec82f0d2-81d0-5004-8e32-0cf8fb934773', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 맑은육개장
  ('ce6f5d8f-164b-5c1d-aef0-8551e951d218', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 망고무스케이크와 마카롱
  ('ce6f5d8f-164b-5c1d-aef0-8551e951d218', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 망고무스케이크와 마카롱
  ('0b85ff62-17b7-5d84-b894-ff65e8121f89', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 매생이순두부탕
  ('0b85ff62-17b7-5d84-b894-ff65e8121f89', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 매생이순두부탕
  ('a2e34070-43f9-5a73-aaac-023704638b66', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 매생이조랭이떡국
  ('888a0266-b876-526f-9c2c-59d7758fadf9', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 매실동치미
  ('888a0266-b876-526f-9c2c-59d7758fadf9', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 매실동치미
  ('20d8e75e-eaaf-5c04-98f3-16ebdce11b33', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 매실장아찌와 과매기구이
  ('5b1e02a0-ece1-5175-9a7f-4b3111ae8b1c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 매운락교무침
  ('5b1e02a0-ece1-5175-9a7f-4b3111ae8b1c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 매운락교무침
  ('2cfc8d18-ce26-5a06-b311-96a694a524c5', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 매운요거트 토마토샐러드
  ('24f55545-4ab8-52c7-93f3-b515b35b36c1', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 맥적구이
  ('0bd82206-4ff1-5ae8-bbef-dda4be583d63', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 머쉬룸 닭스테이크
  ('d7781e27-454b-57e7-95a1-1d13f4fc50b1', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 머스터드튤립치킨
  ('48c99e22-d00a-51e5-9fb8-a0ed45736e1a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 먹골배 카레주먹밥
  ('48c99e22-d00a-51e5-9fb8-a0ed45736e1a', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 먹골배 카레주먹밥
  ('eacf0efb-35ea-5223-a6bd-fc4e3e49a4cd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 멍게비빔밥
  ('d5347a24-667c-5c81-9772-cd5d9ca875a6', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 멜론스프
  ('d5347a24-667c-5c81-9772-cd5d9ca875a6', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 멜론스프
  ('916aa1fa-a75b-5c1e-aee0-f1b361116129', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 멸치 누룽지과자
  ('34e7c15a-70b6-5f5f-b423-8caa397b51a1', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 멸치 찹쌀양념 튀김
  ('d6d92a89-94bb-588a-95fe-ccb1dbbafec3', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 모둠채소 수제피클
  ('2bae1d66-7db2-5ba0-a96d-084449ba2af6', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 모듬탕수
  ('f7bedb2b-919d-5669-9ae5-46c0f7dc1ba0', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 모듬해물찜
  ('4c3296d9-5e91-51c3-9420-98432f414208', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 목살스테이크
  ('3be82cd4-f635-518f-bb20-d21011b553ed', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 무대하무침
  ('3be82cd4-f635-518f-bb20-d21011b553ed', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 무대하무침
  ('8aea1c93-7b15-56b9-a166-2caa736fa474', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 무말랭이김치
  ('8aea1c93-7b15-56b9-a166-2caa736fa474', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 무말랭이김치
  ('72cccbb1-43ca-57a9-9567-199ff1ec14b8', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 무염 겉절이
  ('72cccbb1-43ca-57a9-9567-199ff1ec14b8', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 무염 겉절이
  ('94df3d64-ea16-5278-8a39-ac366993f580', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 무오색쌈
  ('94df3d64-ea16-5278-8a39-ac366993f580', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 무오색쌈
  ('f3dc2ae0-74e1-5267-96fe-0f5a8b51eb42', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 묵계밥
  ('40a7d26e-e263-5472-8650-c2ec6e3983cc', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 묵은지 밀푀유나베
  ('1a611b2d-a536-587c-a20e-fc48209e2cd1', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 묵은지가지말이
  ('f26daf23-642f-5ff5-8590-bbecb934e175', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 묵은지고등어와 새우말이꼬치
  ('f26daf23-642f-5ff5-8590-bbecb934e175', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 묵은지고등어와 새우말이꼬치
  ('502a0160-dd25-5800-b43e-f48d4373cf1a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 묵은지비프롤
  ('62352b9f-4e9d-5c13-a3a8-7ae42430849f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 문어핫바
  ('72a70114-856c-5214-876c-0a447ca7f580', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 미나리명태찜
  ('b53a311b-2329-5e57-ac2f-f7218424e891', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 미나리버섯고기말이&산채소스
  ('b0e7097a-0b94-53d3-b6d3-122663ec0286', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 미니 그라탕
  ('4ff7a5fe-7010-5aa9-92c7-9a25f8fdb831', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 미니 떡갈비
  ('60709587-42ff-50b7-b07e-988d1e7df0e3', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 미니밥버거
  ('60709587-42ff-50b7-b07e-988d1e7df0e3', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 미니밥버거
  ('841a191a-2ae9-58d1-97ff-e53b85847620', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 미니버거
  ('139e16c5-16e6-5cbc-91d1-c5d3b02cf32d', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 미니버섯탕수
  ('c2754e6b-313b-586f-9022-15858ef5f94f', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 미니함박스테이크
  ('f249039d-7c5d-5246-bcbf-c617fb9e8218', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 미숫가루 스콘
  ('f249039d-7c5d-5246-bcbf-c617fb9e8218', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 미숫가루 스콘
  ('295589b1-935f-5fdf-85b2-0df9eb9bc023', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 미역 물오징어 연두부 초회
  ('554d5c25-3c33-589d-b164-f023d96fcca1', 'CUISINE_JAPANESE', 'CUISINE', 'RULE'),  -- 미역 미소국
  ('554d5c25-3c33-589d-b164-f023d96fcca1', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 미역 미소국
  ('95d7e540-3ffb-57c2-a2bf-8a5c6eb639bc', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 미역 조랭이 떡국
  ('b70a41c2-f31c-589f-a34c-7e028dee9080', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 미역볶음밥
  ('d69576ae-b25a-52d1-b08b-0568d1d3b72b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 미역줄기두부무침
  ('941fa081-a1ab-52ee-bb06-d53f0a2244ee', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 민들레 샐러드
  ('3d9f19a4-2cf5-5a12-8078-bb5302e64a67', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 민들레무침
  ('3d9f19a4-2cf5-5a12-8078-bb5302e64a67', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 민들레무침
  ('8abc5d4f-dd3d-509b-9f39-e49f774121e3', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 바나나 생강 크림이 들어간 미숫가루 쿠키슈
  ('ef295798-a3df-5f64-b389-5dc4d463cb3d', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 바나나 속 달걀빵
  ('ef4374b0-9d8c-5a1c-802f-39db09ff9117', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 바나나 스틱
  ('bb98be67-6d6e-504b-94fa-bf28f2f07ba5', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 바나나 쑥두부푸딩
  ('bb98be67-6d6e-504b-94fa-bf28f2f07ba5', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 바나나 쑥두부푸딩
  ('1e896fd2-4ff0-5871-aa95-daea17e87632', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 바나나를 감싼 일본식 달걀말이
  ('152eb30a-26c8-5ff2-b1c5-2db3f0793460', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 바나나미숫가루쉐이크
  ('35df19b0-defa-5926-bf5f-482568196958', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 바나나크림파스타
  ('4799979f-0be5-59a9-9b2c-f6eb81c9a014', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 바비큐리조또
  ('e8f6b208-ffe5-5a82-ab2a-c21642f78742', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 바지락 맑은국
  ('623ad41b-f015-5078-adad-53cb1dc38913', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 바지락 미역국
  ('623ad41b-f015-5078-adad-53cb1dc38913', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 바지락 미역국
  ('ff600285-4702-5484-8622-3e4f5882611c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 바지락매생이전
  ('33b7876c-e181-5944-9c8e-08c2b83fa758', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 바지락실곤약파스타
  ('33b7876c-e181-5944-9c8e-08c2b83fa758', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 바지락실곤약파스타
  ('d982b275-4238-5589-9007-46da762cc762', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 바질 고구마 옥수수빵(플렌타)
  ('717da579-0086-5d27-83f6-a23d3b86fc81', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 바질향이 향긋한 고구마 찰 바케트
  ('21af929c-bd24-5916-8e4d-99227cec4925', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 발사믹소스를 곁들인 오리스테이크
  ('8e8aa1ed-af49-5de7-b7d5-f77066e83dc3', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 밤 라떼
  ('8e8aa1ed-af49-5de7-b7d5-f77066e83dc3', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 밤 라떼
  ('ced91e15-e2b7-5504-88f9-8db964c6a20f', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 밥 샌드위치
  ('e6033798-a0e7-5efd-8ab3-7fef9277d07b', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 밥크로켓
  ('4e1683d9-2a7f-535a-8bf2-d828e49ba6d7', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 방아잎 닭고기말이
  ('93973bb0-3647-57d8-b487-e6883f66208c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 방울토마토 소박이
  ('fd73ac47-d743-545c-b23d-3a8d069f7bdd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 방울토마토를 곁들인 너비아니구이와 쌈밥
  ('9980078f-3fc0-52d9-9de8-4db31dd25da4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 배 깍두기
  ('9980078f-3fc0-52d9-9de8-4db31dd25da4', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 배 깍두기
  ('f6b1fcdd-946c-5404-88af-4ab1009bbcce', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 배·라임 모히또
  ('93285d43-6f68-5baa-913d-c056be06ceb9', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 배물김치
  ('93285d43-6f68-5baa-913d-c056be06ceb9', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 배물김치
  ('c8f1f81f-c642-5177-8dda-0985268ff908', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 배숙구이
  ('78bdbdf1-9cec-5cfd-b32c-7e81f3ace2bd', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 배쨈 식빵
  ('2b81c32e-87d5-5221-9f19-4511650a9d3f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 배차와 주악
  ('2c38b9a4-1898-55c9-8217-bdd44cbdcbaa', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 배추겉절이
  ('2c38b9a4-1898-55c9-8217-bdd44cbdcbaa', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 배추겉절이
  ('05cc90da-0a80-5b8c-aa84-9add85622e83', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 배추된장국
  ('05cc90da-0a80-5b8c-aa84-9add85622e83', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 배추된장국
  ('95665681-ec27-515f-9fd2-5c874a6a35b8', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 배추만두탕
  ('3ab1a7af-018a-56f9-94b9-077887661f93', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 배추토란국
  ('60a0513c-1d31-5217-95c8-dcd08e491673', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 백김치닭살샐러드
  ('00e39579-e632-5ca1-88da-01ccb69b104c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 백김치주꾸미샐러드
  ('18feddff-f909-56bd-9afa-366f7aa2ba6c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 백김치콩비지찌개
  ('29dc368d-2ab1-5b2d-a196-23241b02d981', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 백일송이버섯 볶음
  ('eb010e6c-3f77-531e-b3fe-cd8b29612254', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 백태순두부찌개
  ('e7a8ef75-fefd-55fa-8868-71fb228d6d35', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 버섯 굴소스 볶음
  ('2914eb3b-ce45-5847-891e-e1976907a913', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 버섯 리조또
  ('528dca24-a707-583b-a29a-153fd18ed531', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 버섯만두찜
  ('3980609e-0270-5029-bf6d-2042325831fd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 버섯배추말이
  ('3980609e-0270-5029-bf6d-2042325831fd', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 버섯배추말이
  ('e84579a0-1a03-50e3-8e4d-d55695c5d5bb', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 버섯소스를 곁들인 돼지 등심 스테이크
  ('17b6fb96-3155-582a-b5ed-89a849e558a5', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 버섯순두부찌개
  ('17b6fb96-3155-582a-b5ed-89a849e558a5', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 버섯순두부찌개
  ('7bd60223-d4ba-5d24-a94c-7df1440d3545', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 버섯을 넣은 가지 라자냐
  ('2e14e1bc-d6f0-5278-90fd-14444267d67b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 버섯콩불고기
  ('28d57a21-e777-50e1-b99d-5eae90b232ae', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 버섯탕수
  ('19d18cb2-a513-55c5-9058-0ced9592c814', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 버터치킨카레
  ('541d57e9-ca6a-58a3-87c8-1be407b8faec', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 베이컨 가지 말이
  ('59178dad-7337-5cf0-9505-fd94df0c5519', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 별미병어조림
  ('28dc5eb2-8e26-5813-936c-4bee4633aa97', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 병아리콩 컵케이크
  ('28dc5eb2-8e26-5813-936c-4bee4633aa97', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 병아리콩 컵케이크
  ('5b78a5e7-eb9e-576e-a8de-29a8a2ac3c05', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 복숭아 화채
  ('79e9b94a-de53-5db9-9e11-d4259d87798f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 복숭아샤벳
  ('b3de4775-691a-51ca-85f4-4ef84f53541a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 봄 주먹밥
  ('b3de4775-691a-51ca-85f4-4ef84f53541a', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 봄 주먹밥
  ('73ac6720-62a2-550a-bc1f-687ac294bfaf', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 봄나물밥
  ('bd11b0ca-8652-5bb7-9688-7182eb76e3be', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 봄나물주먹밥
  ('bd11b0ca-8652-5bb7-9688-7182eb76e3be', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 봄나물주먹밥
  ('ba8d36e3-2977-5a5a-8e88-77c84906750b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 부대된장찌개
  ('ba8d36e3-2977-5a5a-8e88-77c84906750b', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 부대된장찌개
  ('d98e42db-a6f3-566b-bd1b-b5ac6244ca28', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 부추 배무침
  ('d98e42db-a6f3-566b-bd1b-b5ac6244ca28', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 부추 배무침
  ('b2fda3f3-1947-5f7f-9319-7af96d24158c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 부추 콩가루 찜
  ('8b8219a5-fa10-554b-8ce7-03888dc92c4b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 부추조개살 콩비지조림
  ('8b8219a5-fa10-554b-8ce7-03888dc92c4b', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 부추조개살 콩비지조림
  ('3a5084e4-a0c3-5c6e-9d3d-121ed18fce05', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 북어비빔밥
  ('3a5084e4-a0c3-5c6e-9d3d-121ed18fce05', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 북어비빔밥
  ('ab0a31cf-2d7a-5a45-8b27-4ac68a63b82c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 불고기 소스를 곁들인 두부구이
  ('85c88114-3737-5ead-9a2f-76928a70a746', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 불고기덮밥
  ('fea000ea-20f6-5217-82cb-4a372b434b69', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 불고기미니볼
  ('c3631e9e-db1f-5f19-8ec9-59e056e2d0f1', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 브로컬리 단호박스프와 완두콩퓨레
  ('c3631e9e-db1f-5f19-8ec9-59e056e2d0f1', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 브로컬리 단호박스프와 완두콩퓨레
  ('7b0054a3-e081-50dd-9967-190852188421', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 브로콜리스프
  ('ab4ba788-d844-53b8-83c0-764182ee179f', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 블랙빈 곤약국수
  ('ce981207-2b8c-51e0-9512-525ec5ef59a0', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 블루베리냉스프
  ('e32660c0-3e6e-564b-b094-d05f08544478', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 블루베리양갱
  ('79628ab7-ff5e-54b5-b46a-668532f5f171', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 비름나물 된장무침
  ('79628ab7-ff5e-54b5-b46a-668532f5f171', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 비름나물 된장무침
  ('d5a8db61-37ac-5cfd-9f4f-7036dbf23eb3', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 비름나물겉절이
  ('d5a8db61-37ac-5cfd-9f4f-7036dbf23eb3', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 비름나물겉절이
  ('f7d43f57-53a6-575b-8d72-24c756da40f0', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 비타 오이 물김치
  ('f7d43f57-53a6-575b-8d72-24c756da40f0', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 비타 오이 물김치
  ('1c374186-1d61-50e1-8af0-d3e363fafb53', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 비트 단호박 파스타
  ('ee1d1896-b22a-5292-9d91-3a20f4d68a92', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 비트 배추피클
  ('329a90dc-050d-5729-9b62-d7e7a4c20660', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 비트두유
  ('7858b22a-5b6e-50bf-8711-81b6a663914f', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 비트무절임
  ('6d8fe269-1e37-5f5a-95e8-e8f24968b66d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 비트양파김치
  ('6d8fe269-1e37-5f5a-95e8-e8f24968b66d', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 비트양파김치
  ('155b4a7d-45bb-5991-9254-022dc53b308a', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 비트와 호두 요리
  ('a7ea3cc2-52f6-597c-93c8-486dbb9c0b68', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 빵 대신 어묵 사라다
  ('a7ea3cc2-52f6-597c-93c8-486dbb9c0b68', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 빵 대신 어묵 사라다
  ('7be5f362-6221-5a14-b588-48acb58d2876', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 사과 견과류 식빵
  ('d6b292ba-5e36-55ce-b752-17638b754514', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 사과 새우 북엇국
  ('8b1187c3-f4ba-5bf6-bf9f-76f9b661548e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 사과 포도주스 조림
  ('8b1187c3-f4ba-5bf6-bf9f-76f9b661548e', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 사과 포도주스 조림
  ('5dcf7292-a2db-5691-a548-2aae38c9d11c', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 사과비트에이드
  ('a719f334-6d25-5c46-a950-8335cfcfeffe', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 사과장아찌
  ('60df63a0-f9ab-5d0e-8dc9-45300335f77e', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 사과채소타코
  ('6edbc83a-be9f-5289-9026-842e4448c27c', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 산나물 유부초밥
  ('e7d67b9b-b769-5278-bb0b-c09c59a1dd59', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 산나물된장찌개
  ('2c8497ec-92bc-53db-922f-c16eb02c9c11', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 산마드래싱과 실곤약 샐러드
  ('d3f2b9f0-b405-5351-a23d-bc75b815617a', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 산채쌀파스타
  ('6036894d-57fe-56cd-919e-a7b7c3aeccc5', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 산호두부
  ('edff46c6-eeb6-53f3-94ec-6c823dc0a75f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 살레마이슈
  ('3829fb3e-3e2b-5b16-bf55-1a01e8b8aae7', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 삼각김밥 빵
  ('3829fb3e-3e2b-5b16-bf55-1a01e8b8aae7', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 삼각김밥 빵
  ('3829fb3e-3e2b-5b16-bf55-1a01e8b8aae7', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 삼각김밥 빵
  ('6462a24f-f28f-5f1d-8627-726113d16fa8', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 삼겹살꼬치구이
  ('e5d5cbe0-567c-59c8-a05e-5a5be6aac8ca', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 삼겹살라면
  ('99e48b8e-d675-5d4e-b733-c2b921aa7be1', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 삼겹살부추찜
  ('f7a4e64b-884e-5225-acf7-757fd0e43f33', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 삼계부대찌개
  ('4264ba02-b364-5ba0-a375-69727b63fbbd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 삼계선
  ('334b0e9e-a4c1-57ff-a69f-f94cdf2daebb', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 삼계치킨
  ('334b0e9e-a4c1-57ff-a69f-f94cdf2daebb', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 삼계치킨
  ('7889d042-b4c4-5fc1-ab9f-bb2b1dfa5128', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 삼계치킨롤
  ('7889d042-b4c4-5fc1-ab9f-bb2b1dfa5128', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 삼계치킨롤
  ('a73e49ab-776a-5875-a325-7efcedc569bd', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 삼색 머핀
  ('a73e49ab-776a-5875-a325-7efcedc569bd', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 삼색 머핀
  ('64a79644-f99b-58c1-88ee-c1a95ef732ef', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 삼색 버터롤
  ('07b66890-1ae9-5d6b-94c2-a9221f6e379a', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 삼색 브레드 스틱
  ('2c285706-e1ac-5ea0-abac-fb9eb838247f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 삼색계란찜
  ('b58c718e-c0c1-5e95-bc9f-5f55ecd0602c', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 삼색꼬치구이
  ('09fa1bf3-f6f9-531f-95ed-9a1c167d21cb', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 삼색나물, 토마토양념장
  ('09fa1bf3-f6f9-531f-95ed-9a1c167d21cb', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 삼색나물, 토마토양념장
  ('63bb3f66-a547-5b06-86ba-c50c01bbe3fb', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 삼색딤섬
  ('48400fc7-d067-5833-838b-277686bcd4a8', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 삼색묵냉국
  ('bbc63723-4dcc-5a07-808e-4a0a3e0346da', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 삼색샌드위치
  ('80167b8c-145d-540b-b167-34b09ca3c2a8', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 삼색채소 냉파스타
  ('22dd898f-4b9c-5865-b20b-8d7456ca5feb', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 삼치튀김, 타르타르소스
  ('d1e39d09-5b3d-58e2-a39c-7ca4b55f899a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 삼합잡채
  ('d1e39d09-5b3d-58e2-a39c-7ca4b55f899a', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 삼합잡채
  ('d1e39d09-5b3d-58e2-a39c-7ca4b55f899a', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 삼합잡채
  ('b4d5143c-e3fe-5595-9ed0-8e42185ce8e2', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 상추 겉절이
  ('b4d5143c-e3fe-5595-9ed0-8e42185ce8e2', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 상추 겉절이
  ('cbabae55-d2fd-5301-88b1-81578a5211b9', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 상큼하자두
  ('5c875aed-9b69-5c51-82c3-4ead2c883a17', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 새뱅이찌개
  ('5c875aed-9b69-5c51-82c3-4ead2c883a17', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 새뱅이찌개
  ('4bf9c5c9-3e71-569f-8610-1e99e425fc81', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 새송이등갈비
  ('1c0b227e-5b72-5422-82e6-91d6bbc2c5b9', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 새싹 비빔밥
  ('2ed89c3b-b20f-5c4c-8881-18a73213a2f8', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 새싹참치김밥
  ('2ed89c3b-b20f-5c4c-8881-18a73213a2f8', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 새싹참치김밥
  ('c08e131c-723f-5fb6-99b6-48f240313c6c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 새우 두부 계란찜
  ('c10160e2-5816-56f7-befb-37ee999a2100', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 새우 속을 채운 리치와 감자튀김
  ('a37ffdaa-ff6c-5d45-bb3d-9868d324f8d0', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 새우 완자탕
  ('e3283c92-f7a8-52df-b889-13e3d38d5d97', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 새우 카레 빠에야
  ('f06e1e55-f0c2-5e33-8f5b-6ea818b20b82', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 새우단배추된장국
  ('747ae681-234b-52c1-9445-b9a450a32a72', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 새우보리죽 고등어스테이크
  ('254cfa9b-53d7-59a0-b807-b10fd7a948d5', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 새우살을 채운 두부 스프(미네스트로네)
  ('5f205984-57e9-5766-939c-76618df1344b', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 새우살토마토스튜
  ('e79bf3f0-8fe7-5686-a2eb-f4df88357a38', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 새우와스테이크
  ('2516da9d-ee59-575f-b1e2-b479de962405', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 새우완자찜
  ('671a5f5a-88ef-5ba8-a910-bfab84454e04', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 새우채소 김치롤
  ('f98d74c4-6cab-5b53-90d2-8777af99fd1a', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 새우채소꼬치구이
  ('f98d74c4-6cab-5b53-90d2-8777af99fd1a', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 새우채소꼬치구이
  ('d402fe91-c18f-526a-bf2a-bb1ce3eed1eb', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 샌드스테이크
  ('ccdb0588-2efd-52b6-b622-7c0c1731cb2f', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 생강향의 고구마 크로켓
  ('ccdb0588-2efd-52b6-b622-7c0c1731cb2f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 생강향의 고구마 크로켓
  ('d79db895-ba6c-55cc-8e60-c31b200131cc', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 생과일 타르트
  ('794de4e7-ce70-5bf6-9d94-4fe9d06d3240', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 생과일과가자미까나페
  ('cbb8113f-3174-566f-b4d4-91d445a754f9', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 생선베이컨말이
  ('975db6ca-cf88-52f8-a699-d090e6d2b894', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 생선살 완자 찹쌀 찜
  ('7710c905-35c2-5f13-b368-9c1b764297e7', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 생선찜 채소말이
  ('81a893d6-8093-59dd-a474-395de5ac772b', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 생선카레튀김
  ('84610ed8-7e01-5d50-8364-c2a7aec5c1ce', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 샤브된장국
  ('f3edbb55-786c-5c19-8164-605fcc9683c0', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 석류 보쌈김치
  ('f3edbb55-786c-5c19-8164-605fcc9683c0', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 석류 보쌈김치
  ('f3edbb55-786c-5c19-8164-605fcc9683c0', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 석류 보쌈김치
  ('0cba7426-f5ea-59d7-93e2-36b6d6a8c984', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 설렁탕쑥떡볶이
  ('0cba7426-f5ea-59d7-93e2-36b6d6a8c984', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 설렁탕쑥떡볶이
  ('824446a6-5c79-5f04-a11b-ca385e3b1c1f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 섭산삼
  ('cd8591f0-0f64-5ddd-88cf-b9cce9f28ed3', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 성주참외동치미
  ('cd8591f0-0f64-5ddd-88cf-b9cce9f28ed3', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 성주참외동치미
  ('57fc57ae-c9ad-590c-b3e3-448f2646cf27', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 세 가지 맛 공기과자
  ('b2294744-2826-5f30-984b-5dddeba73545', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 세 가지곡물콩라미수
  ('029fdb8d-14a1-5861-9824-b38737946a2c', 'CUISINE_JAPANESE', 'CUISINE', 'RULE'),  -- 소고기 가라아게, 칠리소스
  ('7c792528-8b9f-5317-a9ae-1f4339efc1bd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 소고기 김말이
  ('ee0477c6-f6d8-5c71-852d-ddf99998e505', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 소고기 무국
  ('a33edbfd-f572-528e-a669-c01ffa0835b4', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 소고기꼬치구이
  ('b0ae95cb-0f95-5c8b-b696-855b6576eb4e', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 소고기리조또롤
  ('aa09797c-b4fb-5379-be55-e06375e80336', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 소고기육전과 전복내장소스
  ('a7116bdc-28bb-5884-adc9-6dde23fa4fbc', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 소고기찜과 검은콩수제비
  ('2dc79ab2-82f0-57c4-a97c-65250fa0a282', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 소고기채소불고기
  ('4346a4f9-4995-5975-880b-fa82b72fc1ab', 'CUISINE_JAPANESE', 'CUISINE', 'RULE'),  -- 소금없는 초밥
  ('a0e946ad-e7ed-548c-a9f3-739b7256287d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 소안심 야채 호박잎쌈
  ('e30480f4-2852-53de-8f55-bc2efd15ff66', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 속이 든든든한 밥 빵
  ('c2c22aa1-d4b7-5a4e-abfe-2512b16e2c3a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 속채운대저토마토백김치
  ('c2c22aa1-d4b7-5a4e-abfe-2512b16e2c3a', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 속채운대저토마토백김치
  ('38420281-c0c2-57a9-a96d-e65dbc08e22c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 송이 미역국
  ('baa86cf3-08a3-520f-8f8f-9a6116340ea5', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 쇠고기 감자찜
  ('ec9ee6a0-9eea-5e62-a2f5-8d5380d6b650', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 쇠고기말이 쌈밥
  ('1a0188ed-8fd9-5848-8c99-c1baaa7f26cb', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 쇠고기표고찜
  ('1a0188ed-8fd9-5848-8c99-c1baaa7f26cb', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 쇠고기표고찜
  ('9b9b6f55-ff11-5279-9ffb-48c276ad7909', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 수(Sugaer)다(Down) 고구마 케이크
  ('9b9b6f55-ff11-5279-9ffb-48c276ad7909', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 수(Sugaer)다(Down) 고구마 케이크
  ('ed94314b-910b-5d2b-8c9c-c51a5c91512f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 수박 화채
  ('11248de6-a3a2-561b-9ec6-8ad0b33465fd', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 수박껍질풋고추볶음
  ('7f7617f1-b667-5bd4-92f9-491af799eb03', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 수박비빔국수와 수박고추장소스
  ('da4d5c1d-cf9d-5534-aa93-db8bb85ddb7e', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 수삼 냉채
  ('d1278f5e-77fc-5253-b2eb-2f1fd4496234', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 수삼당근정과
  ('6d9982c8-6d4f-5738-90cc-3ff68fba8149', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 수삼떡갈비
  ('0f91be03-a0f4-5e8d-8a4c-b82a4d70f111', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 수삼매운닭찜
  ('fed652b3-1cc9-5944-8c63-a8fd092d4dfd', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 숙주라면
  ('7c107900-d189-547d-acea-10b601ebdf54', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 순두부 사과 소스 오이무침
  ('7c107900-d189-547d-acea-10b601ebdf54', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 순두부 사과 소스 오이무침
  ('fa2acc72-8de7-51a9-b1cc-6e99075931fb', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 순창 고추장 두부강정
  ('fa2acc72-8de7-51a9-b1cc-6e99075931fb', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 순창 고추장 두부강정
  ('fa2acc72-8de7-51a9-b1cc-6e99075931fb', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 순창 고추장 두부강정
  ('bfcbf7ea-f491-5c97-bfe2-a96ad0b4cbec', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 스마일오이피클
  ('143cce29-a141-522d-8ca5-ba4dc06de51a', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 스테이크 샐러드
  ('80479221-ae3d-5b42-9ecb-678b4bdbca04', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 스테이크카나페
  ('fe9a902e-ad09-5de7-bf57-1ea0993b3ef8', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 시금치 리조또
  ('cd2de187-f7f4-5868-8dd8-f9bb9965a42c', 'CUISINE_ASIAN', 'CUISINE', 'RULE'),  -- 시금치 볶음 쌀국수
  ('910bc7ce-f0d5-57bb-9da0-ae107af4cbd4', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 시금치 후무스
  ('a8df31e4-4a1f-577a-81da-2a338c3efeb6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 시금치들깨무침
  ('e9200fcd-8076-5ea9-bc51-7704c24d60a8', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 시금치브로컬리 파스타
  ('ae53b049-5678-5bc4-8c5a-e902fda7cff4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 시금치비트무침
  ('ae53b049-5678-5bc4-8c5a-e902fda7cff4', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 시금치비트무침
  ('8fb3fada-b714-5be6-a969-e8e10f2c75cc', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 시금치크림치즈 빵
  ('ca492cdd-1484-51c3-9cd7-ba8d54c724c4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 시금치토마토무침
  ('ca492cdd-1484-51c3-9cd7-ba8d54c724c4', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 시금치토마토무침
  ('af60651e-d492-5d41-8a9a-5ad486bc5270', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 시래기닭조림
  ('44f5215c-0aee-5044-ae72-875c2777b579', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 시래기리조또
  ('adf3da11-e55c-5d1a-be30-dba3415e8678', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 식혜 팥빙수
  ('adf3da11-e55c-5d1a-be30-dba3415e8678', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 식혜 팥빙수
  ('adf3da11-e55c-5d1a-be30-dba3415e8678', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 식혜 팥빙수
  ('c4112e5d-2efb-5769-b197-174da4488bde', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 식혜양갱과 수박증편
  ('c4112e5d-2efb-5769-b197-174da4488bde', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 식혜양갱과 수박증편
  ('c4112e5d-2efb-5769-b197-174da4488bde', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 식혜양갱과 수박증편
  ('74ef7310-542c-582d-9265-f47f1bb9d27d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 신호등 통통이(어린이 곤약삼색주먹밥)
  ('74ef7310-542c-582d-9265-f47f1bb9d27d', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 신호등 통통이(어린이 곤약삼색주먹밥)
  ('1c19a934-d666-5712-a06c-8a46c0d76ef9', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 실곤약냉파스타
  ('0c195f16-00ed-5b59-9cc4-84256636c379', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 실치오이 초절임
  ('e106c108-45c2-5917-a1c2-982ec904a91f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 쌀콩죽 요거트
  ('f62fcc98-2941-57c6-9924-c440c331a3d2', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 쌈밥
  ('cd56c819-0404-5a39-aef7-5d2830728ea4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 쑥갓부대찌개
  ('cd8caf55-0c35-50fc-a7a0-f440ea9b070f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 쑥호두달걀말이
  ('8ecafb11-f1cb-5480-bc36-e03643fda92b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 아귀나가사키조림
  ('a3ffc9b7-e400-5992-8838-aaf0f56a640b', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 아몬드치킨볼
  ('b02067c1-b498-58b6-bf8d-ab208cf4073d', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 아보카도 달걀 샌드위치
  ('f08b70b1-0eec-53c7-a672-77b8a632b484', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 아삭
  ('e936a7c2-29cf-534c-ab12-a8e2c596b2f4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 아욱 된장무침
  ('e936a7c2-29cf-534c-ab12-a8e2c596b2f4', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 아욱 된장무침
  ('d17afb39-1681-5db0-a019-cc9f83bd9873', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 안심스테이크
  ('13ca0a5d-89f0-571b-8671-fac815ce557b', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 알록달록연근튀김
  ('13ca0a5d-89f0-571b-8671-fac815ce557b', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 알록달록연근튀김
  ('71c01041-68d1-54b4-8706-4559f2a72d96', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 알파벳 스프
  ('ee8cfe70-1e19-54f9-a8ef-52d6645deb09', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 애호박장떡말이밥
  ('0020ba74-a66a-524c-a470-ea3d8ca54f62', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 애호박전 멘보샤와 콩국뇨끼
  ('3e30bed4-e8fa-52e2-8a0b-cf148299431c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 야채 과일 돌구이
  ('4e870d0c-24f5-57f8-a6bf-359b1358fda9', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 야채볶음
  ('1d05a151-61e9-5bee-8ab5-05d9bf658519', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 야채빵
  ('65a70410-4603-5d25-8fb4-a42b51b5c42d', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 야채찜을 곁들인 광어스테이크
  ('ff04bfd0-35d2-59cf-9b75-c794706a7c2d', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 야채칩
  ('4fe048a2-821d-5dbe-8350-92f7526b7f20', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 약밥 닮은 호떡
  ('b45eeb72-fc13-5d2c-926c-354deaa0bb91', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 양배추 고기말이
  ('827aa5e6-4aa2-5d0c-866c-a15a21e39dbd', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 양배추 깻잎 피클
  ('775b7de7-2219-523b-99a7-e5d3b483fa02', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 양배추 쌈말이
  ('34a84866-87ff-5c1c-8af6-b6e541651d12', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 양배추두부찜과 양파케첩소스
  ('50942e25-9542-5b7d-9282-5dd0af7defbd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 양배추롤&참치두부쌈장
  ('44819788-70c4-5c72-a473-b8ff3fa28311', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 양배추말이 김치
  ('44819788-70c4-5c72-a473-b8ff3fa28311', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 양배추말이 김치
  ('37cf54e2-5a11-5969-b2a7-510ee48d5f14', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 양배추버섯말이
  ('f14b86fe-af9d-524b-b8a1-7b39b28667fd', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 양상추샐러드 & 과일드레싱
  ('9b62692a-7991-5c4e-991f-f7675133506f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 양송이버섯과 포도주스를 가미한 등심구이
  ('c4a0531b-0c17-57ef-9573-4d1814ef0bb1', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 양송이크림볶음밥
  ('623c8a78-6a6d-5df8-bc36-82accf339380', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 양지해장국
  ('623c8a78-6a6d-5df8-bc36-82accf339380', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 양지해장국
  ('b53ca9e2-90dd-5c5c-a530-1e6fb712d10e', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 양파토마토스튜
  ('443b8bc9-2ceb-5f45-8efd-11d70f8378f8', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 양파홍초절임
  ('466adff8-5bcb-51de-9e18-14e9e40840c6', 'CUISINE_JAPANESE', 'CUISINE', 'RULE'),  -- 어린이 스시
  ('e0ce5949-f954-5673-a72b-721ae46f2e3e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 어린잎채소 건두부말이
  ('f5a7966f-e564-5590-b218-71a6fdfb7924', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 어묵 잡채
  ('f5a7966f-e564-5590-b218-71a6fdfb7924', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 어묵 잡채
  ('f5a7966f-e564-5590-b218-71a6fdfb7924', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 어묵 잡채
  ('ee378610-9f0d-5fa5-84a7-90ad4bddb6eb', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 얼갈이 무침
  ('ee378610-9f0d-5fa5-84a7-90ad4bddb6eb', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 얼갈이 무침
  ('f9e819e9-6deb-5e3b-9de2-5d92488f4ad6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 얼큰 콩나물 수제비
  ('6b06416e-5f81-51e9-8bad-7b128a6e6b01', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 에멘탈 치즈 퐁듀
  ('a0d6c212-3619-562b-81f6-32ff5ac9c8a8', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 연근고구마샌드위치
  ('7ac8af72-b1c9-5cb5-8317-2e23418f29c2', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 연근샐러드, 흑임자소스
  ('78ea3425-d1cc-5eb0-afb1-dbb420126694', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 연근초무침
  ('da0cda42-20ab-53bb-9377-9d9c8a81a02c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 연두부 무순 냉국
  ('64274c3c-b06f-572a-92d3-8d08f713429b', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 연두부 카프레제
  ('2a124641-6562-51f6-a670-4c648ecaa2a0', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 연어스테이크
  ('682b8119-2e54-5322-9cab-ee5355e32592', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 연어주먹밥튀김
  ('682b8119-2e54-5322-9cab-ee5355e32592', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 연어주먹밥튀김
  ('682b8119-2e54-5322-9cab-ee5355e32592', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 연어주먹밥튀김
  ('a759d421-2e4f-5bed-89a1-ef4982cedb40', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 연어차우더스프
  ('020690a4-b107-5d52-8c73-2804b3c6ef1d', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 연어허브스테이크
  ('5e3322e3-4b50-5c52-a9bf-a9b028828984', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 열무곤약국수
  ('52722a72-0edb-5796-889b-da4ff44bd85e', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 열무김치파스타
  ('dd67103b-676d-50e2-9024-13d242620756', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 열무톳김치
  ('dd67103b-676d-50e2-9024-13d242620756', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 열무톳김치
  ('d146acf9-dfb4-5db0-9ffd-e6c10668a53c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 영양 달걀찜
  ('d146acf9-dfb4-5db0-9ffd-e6c10668a53c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 영양 달걀찜
  ('e0d5bbd9-cd08-5953-814f-42bd3d7ed684', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 영유아를 위한 고소한 닭꼬치
  ('af7eda6c-a56f-58c6-8ebf-cd8257070994', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오렌지삼겹찜
  ('56757916-3411-56bf-b376-8f75be86a17e', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 오렌지와 당근 만남주스
  ('dc9beb73-578b-5438-af84-7a823d9753f5', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오리고기곰피쌈
  ('be2d3b0b-7432-5f25-b3ad-68f5c208fb64', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오리들깨 겨자소스 초무침
  ('54bcfe4e-0bb2-5a85-b59e-e636c78a7dd5', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 오미자 샹그리아
  ('87fcc8a9-ea36-5215-9509-f7065e238cab', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오미자저염나박김치
  ('87fcc8a9-ea36-5215-9509-f7065e238cab', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 오미자저염나박김치
  ('87fcc8a9-ea36-5215-9509-f7065e238cab', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 오미자저염나박김치
  ('b07b0c80-c53d-5886-8923-c3c4d1d4a7fd', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 오븐에 구운 또띠아 칩과 아보카도 딥
  ('b07b0c80-c53d-5886-8923-c3c4d1d4a7fd', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 오븐에 구운 또띠아 칩과 아보카도 딥
  ('775a1ea1-5a92-5946-819d-958645912349', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 오이 소르베
  ('96d7c115-77dc-5ea8-9951-34431473ca1a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오이 짱아지 무침
  ('96d7c115-77dc-5ea8-9951-34431473ca1a', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 오이 짱아지 무침
  ('dcc93608-a7ae-569b-bd8f-32dc3fb9279a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오이냉국을 곁들인 오색쌈밥
  ('83a97874-25ab-5028-bb6f-d51e8c9ded52', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 오이초절임
  ('67e355a2-8841-5a05-9f9c-4477c313f240', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오이파프리카새콤무침
  ('67e355a2-8841-5a05-9f9c-4477c313f240', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 오이파프리카새콤무침
  ('10000000-0000-0000-0000-000000000007', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 오일파스타
  ('8d76069b-9260-5bdc-be30-5e369b540eb4', 'CUISINE_JAPANESE', 'CUISINE', 'RULE'),  -- 오징어 가라아게&칠리소스
  ('681f9576-54f3-5e40-a265-cd22e30e5782', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오징어김치찌개
  ('1a32564a-9b58-5374-9a10-198a3474a6f6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오징어말이 케첩조림
  ('c623300c-5e92-5951-b092-2e8439ecafa1', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오징어보쌈과 저나트륨 된장소스
  ('c623300c-5e92-5951-b092-2e8439ecafa1', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 오징어보쌈과 저나트륨 된장소스
  ('c623300c-5e92-5951-b092-2e8439ecafa1', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 오징어보쌈과 저나트륨 된장소스
  ('b7213c51-2fac-5b3f-b947-dff34a0390c3', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오징어불고기김밥
  ('b7213c51-2fac-5b3f-b947-dff34a0390c3', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 오징어불고기김밥
  ('f5287d87-d75f-52dd-b27c-4fde92f4a153', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오징어와사비마요무침
  ('233169e9-a581-5a8e-9f38-3f679f6937d4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오징어채소말이와 치킨양념소스
  ('233169e9-a581-5a8e-9f38-3f679f6937d4', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 오징어채소말이와 치킨양념소스
  ('233169e9-a581-5a8e-9f38-3f679f6937d4', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 오징어채소말이와 치킨양념소스
  ('2be7fecf-1b49-5f76-948f-cebaa18d9fcd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 오징어토마토초무침
  ('e1a51ea3-097b-57c6-b8db-a0c1831524e6', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 오징어튀김&딸기쨈
  ('c69c06dd-bbc1-52c2-9f59-6b64dcee2f27', 'CUISINE_JAPANESE', 'CUISINE', 'RULE'),  -- 와사비 연어초밥
  ('36e1e167-4485-56dd-b8af-bd96c0e13129', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 와인배숙
  ('2d944826-c917-5c2e-8cf0-d26deca6bd8e', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 완두콩 롤
  ('a0c760a8-22e7-50eb-a83b-d813f9f6a96c', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 완두콩 스프
  ('08a65cf8-4cfd-5c46-8c53-659911c2e2d1', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 완두콩과 당근을 넣은 감자요거트 샐러드
  ('a436355e-0936-5f75-967e-c7924da6fd72', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 완자김치찌개
  ('5849f056-22d9-5b36-86a3-844d35795a2b', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 완자된장국
  ('fe514e61-7c85-5228-8c44-29784710d8ee', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 요거트강화순무김치
  ('fe514e61-7c85-5228-8c44-29784710d8ee', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 요거트강화순무김치
  ('e40886df-a6fe-54ff-abfb-4a87d5ddd308', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 우렁된장소스 배추롤
  ('50cdf014-8f3a-5369-944b-590b74820d21', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 우무오미자냉화채
  ('b37c852c-89a1-5488-8727-4048b67ea0d7', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 우엉들깨무침
  ('b37c852c-89a1-5488-8727-4048b67ea0d7', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 우엉들깨무침
  ('325d3868-952b-5016-a744-0c81a3f15683', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 우유 생강차
  ('74029dae-8e6c-59cc-ad06-89e3d9bd2696', 'CUISINE_JAPANESE', 'CUISINE', 'RULE'),  -- 울금해초밥
  ('ec48c001-6bc1-58ea-b616-8232abef2949', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 원소병
  ('bd66145b-f52b-51ec-b69b-a3cb376988c6', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 웰빙꼬치
  ('627b84bc-fa9a-5a75-8607-2e9feb73cd0a', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 웰빙스테이크
  ('00ea89b4-0bdc-52ea-bed0-45afcf3bdcdc', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 유린기
  ('c8d0538e-a413-5e58-b37d-f78349259b23', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 유부 달걀찜
  ('c8d0538e-a413-5e58-b37d-f78349259b23', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 유부 달걀찜
  ('ff5459c6-ffe3-5ec8-b509-cefc7da421fa', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 유부 만두찜
  ('c3a8c362-1bb2-55ba-aa43-6bb435ece9dd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 유부 우엉 잡채
  ('c3a8c362-1bb2-55ba-aa43-6bb435ece9dd', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 유부 우엉 잡채
  ('c3a8c362-1bb2-55ba-aa43-6bb435ece9dd', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 유부 우엉 잡채
  ('c3a8c362-1bb2-55ba-aa43-6bb435ece9dd', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 유부 우엉 잡채
  ('c9faf358-8d78-5dd6-b817-1c63e0195aba', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 유부초절임
  ('26b3df53-c67e-50ab-89a6-33c5b3517062', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 유자 대구조림
  ('26b3df53-c67e-50ab-89a6-33c5b3517062', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 유자 대구조림
  ('2cde33f7-5a2e-5071-86f0-e9e867f9e0f2', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 유자 치킨 꿔바로우
  ('58b1f202-b628-595b-a75f-43bf6bb70bd9', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 유자등갈비구이
  ('58b1f202-b628-595b-a75f-43bf6bb70bd9', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 유자등갈비구이
  ('ef2f0f30-2ff8-5d85-a6ac-e0db53ab9f19', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 유자샐러드라면
  ('6905d198-2609-5420-a0c2-8a4218dacaa6', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 유자애(愛) 빠지다
  ('fa91c574-0de9-5826-b51b-6c6050cd4320', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 유자향의 곤약 냉채
  ('4073fd72-f641-5da1-89f0-c2edba148f37', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 인삼갈비탕
  ('ba9e100e-8ded-5709-b3a6-ddb7eba16d44', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 인삼닭살찜
  ('7602f816-8a98-5b7b-a18c-48a34307e6ba', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 인삼떡갈비
  ('2feef2bd-0725-53c5-8713-509c3568be40', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 인절미 무스와 수정과 아이스크림
  ('2feef2bd-0725-53c5-8713-509c3568be40', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 인절미 무스와 수정과 아이스크림
  ('2feef2bd-0725-53c5-8713-509c3568be40', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 인절미 무스와 수정과 아이스크림
  ('d86092c6-c581-5e1f-af9a-19833b43cf9e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 일본식 계란말이
  ('a311eed2-c354-5c3c-a34a-a66ed7b8fb9f', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 잎채소튀김
  ('b374ef10-8f5d-51f1-a551-127f74c59214', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 자두 라떼
  ('b374ef10-8f5d-51f1-a551-127f74c59214', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 자두 라떼
  ('355e1648-f8a3-58f2-98be-39077ccd3b0b', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 자색고구마 버터크림 빵
  ('5b2dac01-f627-540a-afa4-d5f9d7f449df', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 자색고구마 호떡
  ('a5662499-4628-54fc-b397-8d440612c4f6', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 자색고구마찹쌀푸딩
  ('a5662499-4628-54fc-b397-8d440612c4f6', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 자색고구마찹쌀푸딩
  ('ddfd8872-0747-50a3-88ac-e763bd45d814', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 잔멸치땅콩볶음
  ('a145b36e-42e0-5b23-ac82-f7fdcdb61555', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 잡곡강정
  ('a145b36e-42e0-5b23-ac82-f7fdcdb61555', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 잡곡강정
  ('a145b36e-42e0-5b23-ac82-f7fdcdb61555', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 잡곡강정
  ('9d95ee16-923c-5a09-8797-21450287a351', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 잡곡강정밥
  ('9d95ee16-923c-5a09-8797-21450287a351', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 잡곡강정밥
  ('9d95ee16-923c-5a09-8797-21450287a351', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 잡곡강정밥
  ('1d53658f-4ae9-5516-9218-154c33e4b011', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 잡채
  ('1d53658f-4ae9-5516-9218-154c33e4b011', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 잡채
  ('1d53658f-4ae9-5516-9218-154c33e4b011', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 잡채
  ('6d95d709-cbf5-5b70-ae52-8d6a2ac006cd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 잣 호두 강정
  ('6d95d709-cbf5-5b70-ae52-8d6a2ac006cd', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 잣 호두 강정
  ('6d95d709-cbf5-5b70-ae52-8d6a2ac006cd', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 잣 호두 강정
  ('3ca14a33-3b01-5d1f-937b-816660cf6624', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 장어조림
  ('3aea9a60-ae2b-5823-9fe9-f81e759f515f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 장어찜
  ('26204cbd-e8dd-5081-b4fb-81fd045dba0a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 저염 겉절이
  ('26204cbd-e8dd-5081-b4fb-81fd045dba0a', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 저염 겉절이
  ('a5bc89b2-6dfc-5eda-b180-a69abb3ef756', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 저염 동치미
  ('a5bc89b2-6dfc-5eda-b180-a69abb3ef756', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 저염 동치미
  ('cd839270-791f-5d84-a5f2-c29452f57a33', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 저염 된장으로 맛을 낸 황태해장국
  ('cd839270-791f-5d84-a5f2-c29452f57a33', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 저염 된장으로 맛을 낸 황태해장국
  ('4a7c43b7-f5de-5ac3-875c-507bf3f1da61', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 저염된장 삼치구이
  ('053098c6-c15a-59c5-8e6b-66a86b4bdbfd', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 저염보쌈김치
  ('053098c6-c15a-59c5-8e6b-66a86b4bdbfd', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 저염보쌈김치
  ('053098c6-c15a-59c5-8e6b-66a86b4bdbfd', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 저염보쌈김치
  ('ec8003a7-1b68-51fa-9eb0-633d8b06a4a0', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 전복리조또
  ('df42b726-ba3b-588c-8e6f-4ab86fad5f7c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 절편꽃말이떡
  ('df42b726-ba3b-588c-8e6f-4ab86fad5f7c', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 절편꽃말이떡
  ('10000000-0000-0000-0000-000000000006', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 제육볶음
  ('ccb81f76-3197-56d2-a5bf-68d04e3a537e', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 제주도 빙떡, 귤간장소스
  ('900de1cc-34e4-5fc0-9629-665317b80c36', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 조개크림파스타
  ('50c4aead-62a3-5eea-8231-b7a260193ec3', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 족발수육
  ('1c37a8a9-44f6-5b89-b8ed-62f07eb343de', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 주꾸미돌나물무침
  ('1c37a8a9-44f6-5b89-b8ed-62f07eb343de', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 주꾸미돌나물무침
  ('2c8cbb04-903e-5c72-903b-dca1f0a119af', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 주꾸미머리순대
  ('0892e309-e5a6-5a19-9ede-b284f69b1230', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 주꾸미연포탕
  ('97974273-6e5c-5916-a5c0-e78a616f24e2', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 죽순콩나물밥
  ('06faf6c0-b75a-52c9-b545-772c280e961d', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 지중해 소스를 곁들인 도미스테이크
  ('e48d22e1-d648-5e9d-8cb8-607db0e63bc6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 쪽갈비구이
  ('e48d22e1-d648-5e9d-8cb8-607db0e63bc6', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 쪽갈비구이
  ('8dc6f555-4dcd-51b9-b0e2-eb794f1f0f8a', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 차가운 당근 수프
  ('8dc6f555-4dcd-51b9-b0e2-eb794f1f0f8a', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 차가운 당근 수프
  ('85700c43-7613-5eb7-8b7c-c4603cf6572a', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 찬밥김치달걀찜
  ('99548955-22e1-5505-8b6d-be3eb3254544', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 찰 어묵찜
  ('581dc11e-4bab-51c4-9520-ab47173174bf', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 참기름 드레싱의 즉석 샐러드
  ('42d55f53-9b27-5b85-a802-128817d30edc', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 참깨가지말이밥
  ('1928f4d1-d725-52bd-8372-769c1ad2d092', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 참나물 매콤함박스테이크
  ('1928f4d1-d725-52bd-8372-769c1ad2d092', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 참나물 매콤함박스테이크
  ('d38a60dd-c941-569f-b1e6-e258400df309', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 참나물 소보로덮밥
  ('d38a60dd-c941-569f-b1e6-e258400df309', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 참나물 소보로덮밥
  ('85d35ace-85e1-5801-9ed1-b9a25aa23892', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 참나물돼지고기샐러드
  ('d88c3b38-5758-568f-b375-db3a5d345498', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 참나물무말이 물김치
  ('d88c3b38-5758-568f-b375-db3a5d345498', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 참나물무말이 물김치
  ('6b8bed42-89e9-58cf-b351-7ed29c97e305', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 참나물페스토 파스타
  ('c8da7834-f312-52aa-8972-66e50ed136ae', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 참외깍두기
  ('c8da7834-f312-52aa-8972-66e50ed136ae', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 참외깍두기
  ('c5bfd34b-4c46-5fad-bbfb-1749d89dbf58', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 참외지 리코타버무리
  ('4839b3c1-dba7-50af-9d2e-595ff0b38086', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 참치 두부 함박스테이크
  ('67788e8a-d530-58a3-9455-6db06431fdfc', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 참치 쇠고기 양상추 쌈
  ('356b8547-c1dc-593c-a82c-360205fee11f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 참치두부 주먹밥
  ('356b8547-c1dc-593c-a82c-360205fee11f', 'OCCASION_LUNCHBOX', 'OCCASION', 'RULE'),  -- 참치두부 주먹밥
  ('93dde75f-6c4a-5ed3-a6e9-9130fb44d148', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 참치비빔밥롤
  ('f1a20f65-2e51-5a6b-8b18-fb85a1ec648e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 찹쌀 흰살 생선찜
  ('d11feb0a-3dac-53cd-9814-928168b3dd4b', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 채소 커리를 곁들인 팬케익
  ('1d96fc01-dc1b-5d90-b557-05c578fe6421', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 채소 팬케익
  ('1e8c0dd4-e024-58b4-9180-04d4e72d2fcd', 'CUISINE_JAPANESE', 'CUISINE', 'RULE'),  -- 채소롤초밥
  ('0d3c3965-88be-5ea5-8fa5-a11371344c4e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 채소말이와 고구마가지구이
  ('27fb660a-99e4-5921-9f0e-3e44a15e620f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 채소비빔밥
  ('dc99805b-6c48-53fe-80c0-19636e2ec18c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 채소어묵
  ('9556ad2e-4319-5db3-aa97-64995bdbdf9d', 'CUISINE_JAPANESE', 'CUISINE', 'RULE'),  -- 채소초밥
  ('a1a98151-e8df-5b41-bca1-dc1fa3ee8be6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 청경채 김치
  ('a1a98151-e8df-5b41-bca1-dc1fa3ee8be6', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 청경채 김치
  ('35ce6642-f4c8-5aab-bfa0-8137c03d0d9d', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 청국장 두부 검은깨 냉스프
  ('0fb573a4-b44f-5391-a798-a113f9bc8bc5', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 청국장 또옥국수
  ('82507e50-a21b-5a96-ab38-808f30356865', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 청국장 시래기전
  ('170aeb90-aad5-50a5-a0b0-d97db6f029a5', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 청국장볶음밥
  ('76c207fb-139a-5aff-9d8b-50820131b34f', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 청국장소스 연어 스테이크
  ('0c9f66b7-4636-5f49-91f1-c02e3167b28e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 청국장지짐 케일쌈밥
  ('78dc5e01-7040-5692-b8f1-9063e6cc4ccc', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 체리젤리를 곁들인 고구마무스
  ('78dc5e01-7040-5692-b8f1-9063e6cc4ccc', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 체리젤리를 곁들인 고구마무스
  ('2cf5ae20-ab4d-5639-be14-85c2ad6c0d77', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 초코바나나 두부크림빵
  ('25afe444-9535-51de-88f9-c6fa4451103d', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 초코치즈크림케이크
  ('25afe444-9535-51de-88f9-c6fa4451103d', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 초코치즈크림케이크
  ('44f8022b-fa7b-56fd-b601-27017408dfd3', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 초코타르트
  ('5e75d8d0-7d91-5c14-9a1a-7ded2bc45738', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 취나물들깨무침
  ('5e75d8d0-7d91-5c14-9a1a-7ded2bc45738', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 취나물들깨무침
  ('fe0d5688-8458-5424-97ba-422c55e15377', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 치아바타 피자빵
  ('fe0d5688-8458-5424-97ba-422c55e15377', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 치아바타 피자빵
  ('8dfea60e-1f74-5e64-b934-76bf63dd2581', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 치자연근물김치
  ('8dfea60e-1f74-5e64-b934-76bf63dd2581', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 치자연근물김치
  ('726c50f2-b007-5e79-bde1-6d7f68e91a66', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 치즈 두부 튀김
  ('926c9134-f306-51db-a2cd-1caa9807c9e9', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 치즈감자크로켓
  ('926c9134-f306-51db-a2cd-1caa9807c9e9', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 치즈감자크로켓
  ('377415a7-7936-56b1-932a-dd5f852bba49', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 치즈리조또
  ('05b741d1-10e0-5a0d-8515-74b7d7c75a48', 'CUISINE_ASIAN', 'CUISINE', 'RULE'),  -- 치커리샐러드와 올리브 마늘 소스
  ('55bc4528-8a25-5406-b7e9-c5918763a86d', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 치킨 버거 스테이크
  ('55bc4528-8a25-5406-b7e9-c5918763a86d', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 치킨 버거 스테이크
  ('5698ea1e-9802-5919-a70c-45ae0e077d31', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 치킨 쇠고기 땅콩소스 꼬치
  ('5698ea1e-9802-5919-a70c-45ae0e077d31', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 치킨 쇠고기 땅콩소스 꼬치
  ('7ff88ac7-b37a-54f0-b05f-124da95e4443', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 치킨완자스프
  ('7ff88ac7-b37a-54f0-b05f-124da95e4443', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 치킨완자스프
  ('b38fb10d-5d82-5171-a911-d95680b9d0d8', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 칠곡석류국수
  ('0ede1e6d-c870-596e-8cac-2028d6ed219e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 카레가자미조림
  ('c88a7d90-82a9-5560-9486-a19617cdfcd9', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 카레삼겹살 파니니
  ('0f634d14-3ba7-5a2d-b81e-382ac5af55d6', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 카레소스를 얹은 두부 스테이크
  ('8e30fc97-321f-5a1c-bb89-125a0a175e96', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 카레크림 두부면 파스타
  ('db80a83e-d2f4-5612-9875-5f1afc22fc5e', 'CUISINE_ASIAN', 'CUISINE', 'RULE'),  -- 카레탄두리치킨과 닭가슴살냉채
  ('db80a83e-d2f4-5612-9875-5f1afc22fc5e', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 카레탄두리치킨과 닭가슴살냉채
  ('76e67045-c52e-51a8-8371-64626807770b', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 카스텔라케이크와 해독주스
  ('76e67045-c52e-51a8-8371-64626807770b', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 카스텔라케이크와 해독주스
  ('aa22b607-9249-5f18-9ae9-7a4ec298beb6', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 카카오 깜빠뉴
  ('d82e8ea8-5dfc-5b58-b71c-47e07a3afaf5', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 커리향이 들어간 치킨샐러드 샌드위치
  ('d82e8ea8-5dfc-5b58-b71c-47e07a3afaf5', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 커리향이 들어간 치킨샐러드 샌드위치
  ('c432158d-a7f6-52de-855b-f3707a774726', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 컬리플라워소스 광어스테이크
  ('c432158d-a7f6-52de-855b-f3707a774726', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 컬리플라워소스 광어스테이크
  ('debd79e6-5fee-5910-851d-32d079eaef15', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 케일 오미자 샐러드
  ('969dc763-dc64-5624-94f6-d5505898fa8c', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 코다리 뿌리채소 들깨탕
  ('cb668f8c-ddba-543b-ae6e-8816f7b467b8', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 코다리맑은찜
  ('cb668f8c-ddba-543b-ae6e-8816f7b467b8', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 코다리맑은찜
  ('e4ea068f-5dbb-59a0-90bb-6223d47ca87f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 코마레티
  ('91222017-05d1-5c0c-9849-fb7ba7282e6b', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 코울슬로샐러드
  ('bd28b65a-4823-5ab1-aeff-e03ca3243fd2', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 코코넛 수플레 치즈케이크
  ('bd28b65a-4823-5ab1-aeff-e03ca3243fd2', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 코코넛 수플레 치즈케이크
  ('99aa9991-d304-566e-8c34-c46a3aef0600', 'CUISINE_ASIAN', 'CUISINE', 'RULE'),  -- 코코넛밀크카레
  ('36ced6ba-3178-517a-86b6-288589e8f5b7', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 콜라비 깍두기
  ('36ced6ba-3178-517a-86b6-288589e8f5b7', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 콜라비 깍두기
  ('a7c86035-a98b-5f8b-8c4f-27eaca1308e8', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 콜라비물김치
  ('a7c86035-a98b-5f8b-8c4f-27eaca1308e8', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 콜라비물김치
  ('92882633-0835-54d9-a957-7cd37823a2b2', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 콜라비오미자 물김치
  ('92882633-0835-54d9-a957-7cd37823a2b2', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 콜라비오미자 물김치
  ('b17b423b-6169-58d0-b38f-42f57ed624db', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 콜리플라워미역국
  ('b17b423b-6169-58d0-b38f-42f57ed624db', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 콜리플라워미역국
  ('612ecc17-86c5-5238-a121-fece65d771dd', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 콩 판나코타
  ('38320a8b-b742-556d-a176-d0c30f076d57', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 콩가루 고구마 타르트
  ('555a0eaa-e761-5f26-af98-8fde66130472', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 콩가루 소보로
  ('208399d1-1f71-5e82-aabf-3a3d0531447b', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 콩가루 쑥 다쿠아즈
  ('13320da2-f5b6-5d4f-a3aa-6426adf50847', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 콩가루비빔밥
  ('e189e443-c1b1-5fbf-a62a-b3676c936a45', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 콩감태유자 스콘
  ('e189e443-c1b1-5fbf-a62a-b3676c936a45', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 콩감태유자 스콘
  ('960e27b8-ed91-5c51-a704-3ed46baa9bda', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 콩나물부추볶음
  ('a0809caf-bf32-53a0-99ad-f9d7cf125bbe', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 콩비지포카치아 샌드위치
  ('b276493c-730d-5977-8503-8afb222c7f2d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 쿠스쿠스 삼계롤
  ('73d78518-798d-5fb7-ad58-0999d07c2751', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 퀴노아 팥수프
  ('a562a3ed-3d37-54e5-a74d-7b7b93d10eb9', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 크랜베리 샤브레 쿠키
  ('6a005436-19ea-5db0-b5eb-52c9005c339d', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 크랜베리 소보로 떡
  ('48077b1d-1b2e-5c03-a53f-6db02d95c98e', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 크림소스치킨롤
  ('9c7e742d-bb5c-5dc4-a0f5-e22ebb82bb2a', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 크림치즈 망고무스
  ('9c7e742d-bb5c-5dc4-a0f5-e22ebb82bb2a', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 크림치즈 망고무스
  ('970b2f50-a23f-5524-bef3-878fe29172e4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 크림치즈떡볶이
  ('970b2f50-a23f-5524-bef3-878fe29172e4', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 크림치즈떡볶이
  ('ef851787-596a-5715-9ebd-1e5e82de6976', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 크림카레소스를 곁들인 함초두부스테이크
  ('83918ac1-b6db-5d3e-ab60-456ac5e973fa', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 클럽 샌드위치
  ('83918ac1-b6db-5d3e-ab60-456ac5e973fa', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 클럽 샌드위치
  ('a77f9b8c-aca4-5d66-9919-9eecc3bbc009', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 클로－바
  ('a3b28209-47d5-5c7e-8187-840c72bf709f', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 키조개샤브샤브
  ('230a68b6-b7a0-5d2c-84a2-052bdd8499f9', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 타이거새우 현미볶음밥
  ('37998780-30e5-56e2-89e3-3362b0918159', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 타콤소스닭조림
  ('e906eed0-bc4d-5c34-ac98-ae067605134d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 태국식 불고기 샐러드
  ('e906eed0-bc4d-5c34-ac98-ae067605134d', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 태국식 불고기 샐러드
  ('100b2af4-bc0a-5d7e-a771-6d6851356ed8', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토란순두부찌개
  ('e2332e26-cd31-5cce-90d2-7dc2373551e4', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 토마토 가지 카프레제
  ('f584b623-1678-55a3-8b10-64be7a986264', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 토마토 미소 된장국
  ('f584b623-1678-55a3-8b10-64be7a986264', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 토마토 미소 된장국
  ('50e35496-5cd7-58a9-a17a-53c0323e0daa', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토 비빔국수
  ('630c96d4-70c9-5862-aee8-3b7bb3c3e00d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토 채소 계란찜
  ('d600ccdf-bdab-5eb2-b2e7-e6fe70c17e70', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토김치
  ('d600ccdf-bdab-5eb2-b2e7-e6fe70c17e70', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 토마토김치
  ('095f434f-db1e-599d-ad15-72d925dd7a16', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토돼지고기해장국
  ('095f434f-db1e-599d-ad15-72d925dd7a16', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 토마토돼지고기해장국
  ('00d8c2d5-4b48-5e2e-bf36-6d1844d0cda6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토떡볶이
  ('00d8c2d5-4b48-5e2e-bf36-6d1844d0cda6', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 토마토떡볶이
  ('338bf09d-bf14-5c6a-a193-6ccb67ce0644', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 토마토라면
  ('5796e287-e68b-5981-bd52-11d86904f430', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 토마토맑은장국
  ('3ab8b825-480c-5f64-9ef1-6adb87834ea7', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 토마토샐러드라면
  ('b08d4cc4-e448-5857-95ed-eee4573070d3', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토소고기장조림
  ('e5073927-6ab8-5977-80e0-9717ae5d4937', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토소고기찜
  ('068f0680-e837-5684-8706-7e6f53cd5a48', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 토마토소박이
  ('77bfaf7f-7752-5914-bd8f-ad9923d2dd7c', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 토마토소스 애호박냉파스타
  ('5fcd19d4-3f76-5e7a-bcf6-ef72b5a5e2a2', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토소스닭갈비
  ('2b7adfce-19b6-5e74-b3a0-324a0b33ddf4', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 토마토스프파스타
  ('c5d03dc1-69bd-5eaf-9cdb-cff0334abd67', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 토마토시금치 닭볶음탕
  ('3080240e-55eb-5f59-aa56-fdce347c3390', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토요구르트된장국
  ('0fa853c6-0ef0-5363-bac1-191e91668b00', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토제철나물 샐러드
  ('0fa853c6-0ef0-5363-bac1-191e91668b00', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 토마토제철나물 샐러드
  ('555647bf-0560-5b21-82f6-b9441cebdb50', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 토마토젤리
  ('555647bf-0560-5b21-82f6-b9441cebdb50', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 토마토젤리
  ('f9fca770-30a8-5a37-8f12-106b5475ec9b', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 토마토주스를곁들인컵토마토
  ('f9fca770-30a8-5a37-8f12-106b5475ec9b', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 토마토주스를곁들인컵토마토
  ('d32f4f34-5278-52c9-bcc1-7a7ad5656d9c', 'CUISINE_FUSION', 'CUISINE', 'RULE'),  -- 토마토채소스프와 연어소바찜
  ('487849b5-170f-59ae-9394-1cd6f02320af', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토카레 채소볶음밥
  ('56d8cc17-b06d-518c-a450-143ba0b33b85', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 토마토콩조림
  ('5f56f381-f538-5ddc-8d82-c942bf6b96e7', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 톡톡! 오미자 펀치
  ('1efc8b5f-ba3d-5f0d-b826-2b745296eca4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 톳나물 두부무침
  ('1efc8b5f-ba3d-5f0d-b826-2b745296eca4', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 톳나물 두부무침
  ('53d9bea3-8ef0-5aae-8b3d-5402a54cd8cb', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 통도라지양념구이
  ('81c8f73d-d4db-5c42-ba39-33d03e45ecbe', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 통배추 겉절이
  ('81c8f73d-d4db-5c42-ba39-33d03e45ecbe', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 통배추 겉절이
  ('f127a0e1-3f0e-5a03-b216-e184b91e74da', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 통삼겹맥적구이
  ('a67d798a-5ca5-51d6-896f-bcf2668acdca', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 통삼겹스테이크
  ('da55bfc7-1641-5633-af9d-20a46c11f277', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 파인애플떡갈비
  ('8dd7eecd-8c5d-5309-baf7-64c9f7c7f729', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 파인애플볶음밥
  ('984bf14f-8aa0-5ab6-b27d-c11d1e547e74', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 파프리카 물김치
  ('984bf14f-8aa0-5ab6-b27d-c11d1e547e74', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 파프리카 물김치
  ('3017f55d-419b-5ca1-82d3-565984f02c18', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 파프리카깍두기(어린이용)
  ('bb51d339-a20a-51d1-8dff-5381f786f834', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 파프리카볶음밥
  ('1082e48c-9688-5f3f-8e23-736d375b95a7', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 팽이버섯 야채볶음
  ('4e7709a3-34c5-5143-82d3-2916acbfd493', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 펌킨크로켓
  ('56c793f2-6c3b-524f-bd03-b761bc46cd11', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 펜네 파스타 샐러드
  ('1abfa0af-5849-5f57-b108-32c6dafe9e98', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 포니언 스프
  ('4382d107-752e-52c1-8e0f-98da756b8d51', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 포도청 보리 수단
  ('c0b0174f-9fd3-53cd-b745-a8122e8b061f', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 포도호두스무디
  ('df80a3f8-87ba-5cdc-b160-d59d2e67da69', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 포두부채소잡채
  ('df80a3f8-87ba-5cdc-b160-d59d2e67da69', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 포두부채소잡채
  ('df80a3f8-87ba-5cdc-b160-d59d2e67da69', 'OCCASION_HOLIDAY', 'OCCASION', 'RULE'),  -- 포두부채소잡채
  ('3471f501-6ee9-5b4f-9a41-beb1085d0dfa', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 포항초사과샐러드
  ('9f4bd9ac-cb74-58be-b563-4eb8f5b2ec2f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 표고버섯 감자찜
  ('c4a348ac-4eec-588e-ac33-6c83173fe86f', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 표고버섯 청경채국
  ('c53c047c-84b3-56a4-9d9d-8a44e257b0fe', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 표고버섯쇠고기찜
  ('c53c047c-84b3-56a4-9d9d-8a44e257b0fe', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 표고버섯쇠고기찜
  ('ebf3be65-a169-557f-80d1-f9e1f9e54fbb', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 표고크림파스타
  ('512df953-6219-5b4b-be19-496456f3ba3e', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 풋마늘오징어김치
  ('512df953-6219-5b4b-be19-496456f3ba3e', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 풋마늘오징어김치
  ('667f339a-6a76-568b-a970-ee0aea076597', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 퓨전떡갈비
  ('653f1570-ed58-5ecd-ba54-278bbd948ab6', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 한식풍닭가슴살스테이크
  ('d06fde9f-fd3c-50ba-a000-0053ae5cea40', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 한우굴라쉬
  ('551b9663-40e7-5f20-beec-42ae23e46798', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 한입꼬치구이
  ('ed06bdd4-750d-56ce-a780-7fd0d96455a9', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 함박스테이크
  ('9d5d185e-1d05-59e8-858e-e0932614cde6', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 함박스테이크 볼 밥
  ('4482f537-4d39-5241-bf1f-5de47777f714', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 함박스테이크&토마토카레소스
  ('4482f537-4d39-5241-bf1f-5de47777f714', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 함박스테이크&토마토카레소스
  ('4e5ba725-27e0-5d87-a1f2-aae641698edd', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 함초 냉이 국수
  ('41c98bed-6739-5faa-bab9-abc43f75a7b7', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 함초김치
  ('41c98bed-6739-5faa-bab9-abc43f75a7b7', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 함초김치
  ('03df6aff-ed1c-503d-b70e-e4858a966b58', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 함초떡갈비
  ('fcd6ae43-7fbb-5940-bcd1-4da70e201c57', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 함초소고기말이
  ('1fc7b186-52e6-5e10-9ef3-720bc9c32946', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 해물김치찌개
  ('1fc7b186-52e6-5e10-9ef3-720bc9c32946', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 해물김치찌개
  ('4e6634fa-e2b2-5dd2-8ccc-aa559560813f', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 해물볶음밥
  ('bc054ad1-b814-5d21-ad01-f2a4e539c19c', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 해물순두부된장찌개
  ('28651fb4-6023-53a2-b93e-c88958f019bb', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 해물애호박 전병말이
  ('f338ecab-329f-50be-8d7e-e1240343bcce', 'CUISINE_JAPANESE', 'CUISINE', 'RULE'),  -- 해물우동볶음
  ('7f33c559-e938-5cf8-bec7-7b81b56c72e4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 해물토마토김치찌개
  ('7f33c559-e938-5cf8-bec7-7b81b56c72e4', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 해물토마토김치찌개
  ('01a27af0-3286-5460-bfa4-32e8341ac249', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 해산물두부샌드
  ('633e34f0-86bd-5561-bb13-77880aa64875', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 해산물리조또
  ('56c29202-dc9f-5a09-a17f-98d0f290fc63', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 해조비빔국수
  ('5c705c73-caad-5bce-b855-b8bdd6fcb648', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 해초갈비찜
  ('5c705c73-caad-5bce-b855-b8bdd6fcb648', 'OCCASION_GUEST', 'OCCASION', 'RULE'),  -- 해초갈비찜
  ('b81a6208-035f-5a45-a04a-d1d170f0499f', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 해초탕수
  ('7377e6db-95a5-5968-b206-bbe8bd36b4f0', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 햄버거스테이크
  ('728b9c63-a7e9-58f4-af3e-e3143748f52d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 향향 볶음밥
  ('5936cf9d-736e-5419-9d15-14c7fdffed62', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 허브닭스테이크
  ('8f14c852-16b4-5c4a-9401-c10327707f6d', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 현미 크레이프
  ('08e0c0ba-5f81-56e8-b468-9260db1ec0e0', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 호두 사과 샐러드
  ('08e15cff-9544-5b47-bcfb-5180d5379d31', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 호두떡볶이
  ('08e15cff-9544-5b47-bcfb-5180d5379d31', 'OCCASION_LATE_NIGHT', 'OCCASION', 'RULE'),  -- 호두떡볶이
  ('4d395e04-c187-5f80-8910-fd03ee311ac5', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 호박 고구마 스프
  ('5ddbee68-90b9-5657-a5e8-fac8cbcf11a0', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 호박잎 삼계탕
  ('320b93fa-6e89-5353-93d1-5e86bc963fd6', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 호박잎다슬기된장국
  ('320b93fa-6e89-5353-93d1-5e86bc963fd6', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 호박잎다슬기된장국
  ('d46d9aae-d163-5d72-9dde-7092dadfce16', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 호박잎삼계
  ('5cb39b80-ff44-5bd9-a454-e39f43b15b32', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 홍시 생밤 무침
  ('5cb39b80-ff44-5bd9-a454-e39f43b15b32', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 홍시 생밤 무침
  ('cf400e1d-e0b1-5323-a339-73dad3dfd50d', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 홍시 쉐이크
  ('d818b266-6d98-5d7a-b6f6-e437c44d9c63', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 홍시곶감화채
  ('df37f5d1-dfe9-5176-8d3c-86243dada435', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 홍시에이드
  ('122f2e2d-f26d-5787-b747-8b9ad6660871', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 홍시잼 식빵
  ('295a2254-68f7-543c-b958-568f2b2d6173', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 홍합 배춧국
  ('64acbe50-1130-5f03-b1c5-d334ed23a8c4', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 황금팽이 비빔국수
  ('dc7ccd42-f28e-5f18-8139-12e118225f39', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 황태두부무국
  ('dc7ccd42-f28e-5f18-8139-12e118225f39', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 황태두부무국
  ('39a311b4-dc2e-5a6e-8959-13e1a0c5226d', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 황태리조또
  ('39a311b4-dc2e-5a6e-8959-13e1a0c5226d', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 황태리조또
  ('bf8dce51-896c-599a-b34b-97db5ee59033', 'CUISINE_WESTERN', 'CUISINE', 'RULE'),  -- 황태미역 곤약스프
  ('bf8dce51-896c-599a-b34b-97db5ee59033', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 황태미역 곤약스프
  ('d184b49e-492f-5d35-ab63-977770451122', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 황태미역국
  ('d184b49e-492f-5d35-ab63-977770451122', 'OCCASION_DIET', 'OCCASION', 'RULE'),  -- 황태미역국
  ('d184b49e-492f-5d35-ab63-977770451122', 'OCCASION_HANGOVER', 'OCCASION', 'RULE'),  -- 황태미역국
  ('b58d1331-514e-56a4-9bc9-fa6d95b299b7', 'CUISINE_CHINESE', 'CUISINE', 'RULE'),  -- 황태탕수육
  ('d359978e-f9d3-55a5-9b60-6905b88ebc6d', 'CUISINE_KOREAN', 'CUISINE', 'RULE'),  -- 효도강정
  ('d359978e-f9d3-55a5-9b60-6905b88ebc6d', 'OCCASION_DRINKING_SNACK', 'OCCASION', 'RULE'),  -- 효도강정
  ('d359978e-f9d3-55a5-9b60-6905b88ebc6d', 'OCCASION_SNACK', 'OCCASION', 'RULE'),  -- 효도강정
  ('2b365219-0e30-53c0-868a-afc00d5af6a8', 'CUISINE_KOREAN', 'CUISINE', 'RULE')  -- 흑마늘견과류조림
ON CONFLICT (recipe_id, tag_code) DO NOTHING;

-- 넣은 결과를 눈으로 확인하고 커밋한다. 숫자가 예상과 다르면 ROLLBACK.
SELECT axis_code, tag_code, count(*) FROM recipe_tags
 WHERE assigned_by = 'RULE' GROUP BY 1,2 ORDER BY 1, 3 DESC;

COMMIT;
