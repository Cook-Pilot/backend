package com.cookpilot.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.cookpilot.backend.review.PhotoUploadFailedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사진 업로드 실패 매핑. MockMvc 는 multipart 파싱을 거치지 않아 크기 초과를 재현할 수 없으므로
 * (실서버에서는 11MB 업로드로 413 을 실측했다) 핸들러 매핑 자체를 회귀 대상으로 잡아 둔다.
 */
class UploadErrorMappingTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void 한도_초과는_413() {
		ProblemDetail problem = handler.handlePayloadTooLarge(
				new MaxUploadSizeExceededException(10 * 1024 * 1024));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
		assertThat(problem.getDetail()).contains("10MB");
	}

	@Test
	void 저장소_장애는_500이고_원인을_노출하지_않는다() {
		ProblemDetail problem = handler.handlePhotoUploadFailed(
				new PhotoUploadFailedException("사진 업로드에 실패했습니다.",
						new RuntimeException("credentials not found: arn:aws:iam::...")));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
		assertThat(problem.getDetail()).doesNotContain("aws");
	}
}
