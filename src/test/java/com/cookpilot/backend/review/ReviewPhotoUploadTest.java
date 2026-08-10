package com.cookpilot.backend.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/reviews/photos 계약 테스트. 스토리지가 목이라 DB/Docker 불필요 —
 * 기본 프로파일 + h2 컨텍스트로 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewPhotoUploadTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 이미지_업로드는_201과_목_url을_돌려준다() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

		mockMvc.perform(multipart("/api/v1/reviews/photos").file(file))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.url", startsWith(ReviewPhotoService.MOCK_URL_PREFIX)));
	}

	@Test
	void 빈_파일은_400() throws Exception {
		MockMultipartFile empty = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[0]);

		mockMvc.perform(multipart("/api/v1/reviews/photos").file(empty))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 이미지가_아니면_400() throws Exception {
		MockMultipartFile text = new MockMultipartFile(
				"file", "note.txt", "text/plain", "not an image".getBytes());

		mockMvc.perform(multipart("/api/v1/reviews/photos").file(text))
				.andExpect(status().isBadRequest());
	}

	@Test
	void file_파트가_없으면_400() throws Exception {
		mockMvc.perform(multipart("/api/v1/reviews/photos"))
				.andExpect(status().isBadRequest());
	}

}
