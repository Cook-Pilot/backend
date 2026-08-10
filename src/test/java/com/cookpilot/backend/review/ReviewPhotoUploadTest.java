package com.cookpilot.backend.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.user.UserService;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/reviews/photos 계약 테스트. 업로드가 베타 사용자 세션을 요구하므로
 * 다른 API 테스트와 동일하게 {@link PostgresApiTestBase}(db 프로파일 + 데모 사용자 헤더)를 쓴다.
 */
class ReviewPhotoUploadTest extends PostgresApiTestBase {

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
	void 사용자_헤더가_비어_있으면_401() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

		mockMvc.perform(multipart("/api/v1/reviews/photos").file(file)
						.header(UserService.USER_ID_HEADER, ""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER_SESSION_REQUIRED"));
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
