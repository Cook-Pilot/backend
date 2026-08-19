package com.cookpilot.backend.review;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.user.UserService;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/reviews/photos 계약 테스트. 업로드가 베타 사용자 세션을 요구하므로
 * 다른 API 테스트와 동일하게 {@link PostgresApiTestBase}(db 프로파일 + 데모 사용자 헤더)를 쓴다.
 *
 * 버킷을 빈 값으로 고정해 목 모드를 강제한다 — 개발자 환경에 PHOTOS_BUCKET 이 설정돼 있어도
 * 테스트가 실제 S3 로 새지 않는다.
 */
@TestPropertySource(properties = "cookpilot.photos.bucket=")
class ReviewPhotoUploadTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	/** 디코더를 통과하는 진짜 이미지. 업로드가 재인코딩으로 메타데이터를 떨어내므로 필요하다. */
	private static byte[] imageBytes(String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(24, 18, BufferedImage.TYPE_INT_RGB), format, out);
		return out.toByteArray();
	}

	@Test
	void 이미지_업로드는_201과_목_url을_돌려준다() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", imageBytes("jpg"));

		mockMvc.perform(multipart("/api/v1/reviews/photos").file(file))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.url", startsWith(ReviewPhotoService.MOCK_URL_PREFIX)));
	}

	@Test
	void 사용자_헤더가_비어_있으면_401() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", imageBytes("jpg"));

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
	void 화이트리스트에_없는_이미지_형식은_400() throws Exception {
		MockMultipartFile gif = new MockMultipartFile(
				"file", "photo.gif", "image/gif", imageBytes("png"));

		mockMvc.perform(multipart("/api/v1/reviews/photos").file(gif))
				.andExpect(status().isBadRequest());
	}

	@Test
	void url에는_content_type에_맞는_확장자가_붙는다() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.png", "image/png", imageBytes("png"));

		mockMvc.perform(multipart("/api/v1/reviews/photos").file(file))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.url", endsWith(".png")));
	}

	@Test
	void 저장_경로에_업로더가_박힌다() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", imageBytes("jpg"));

		mockMvc.perform(multipart("/api/v1/reviews/photos").file(file))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.url",
						startsWith(ReviewPhotoService.MOCK_URL_PREFIX + DEMO_USER_ID + "/")));
	}

	@Test
	void 이미지인_척하는_파일은_400() throws Exception {
		MockMultipartFile fake = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

		mockMvc.perform(multipart("/api/v1/reviews/photos").file(fake))
				.andExpect(status().isBadRequest());
	}

	@Test
	void file_파트가_없으면_400() throws Exception {
		mockMvc.perform(multipart("/api/v1/reviews/photos"))
				.andExpect(status().isBadRequest());
	}

}
