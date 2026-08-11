package com.cookpilot.backend.review;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.cookpilot.backend.user.UserService;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 리뷰 사진 업로드. 클라이언트가 받은 URL 을 {@code POST /reviews} 의 photoUrls 에 넣는다.
 *
 * 여러 장은 이 업로드를 장수만큼 반복한다 — 장당 한 요청이라 실패한 장만 재시도하면 된다.
 *
 * 다른 개인화 API 와 동일하게 베타 사용자 세션을 요구한다. 업로드는 과금 지점이라
 * 무인증 호출을 막는다.
 *
 * 버킷이 설정되지 않으면 저장 없이 목 URL 을 돌려준다 — 로컬 개발은 AWS 자격증명 없이
 * 그대로 돌아간다(Gemini 키 미설정 시 폴백과 같은 방식).
 */
@Service
public class ReviewPhotoService {

	static final String MOCK_URL_PREFIX = "https://mock-storage.cookpilot.local/review-photos/";

	static final String KEY_PREFIX = "review-photos/";

	/**
	 * 허용 이미지 형식과 저장 확장자. content-type 은 클라이언트 신고값이라 위조할 수 있지만,
	 * 화이트리스트로 최소한 스크립트·실행파일이 이미지인 척 올라오는 건 막는다.
	 */
	private static final Map<String, String> ALLOWED_TYPES = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp",
			"image/heic", "heic");

	private final UserService userService;
	private final ObjectProvider<S3Client> s3ClientProvider;
	private final String bucket;
	private final String region;

	public ReviewPhotoService(
			UserService userService,
			ObjectProvider<S3Client> s3ClientProvider,
			@Value("${cookpilot.photos.bucket:}") String bucket,
			@Value("${cookpilot.photos.region:ap-northeast-2}") String region) {
		this.userService = userService;
		this.s3ClientProvider = s3ClientProvider;
		this.bucket = bucket;
		this.region = region;
	}

	public String upload(MultipartFile file) {
		userService.getCurrentUser();
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("파일이 비어 있습니다.");
		}
		String extension = ALLOWED_TYPES.get(file.getContentType());
		if (extension == null) {
			throw new IllegalArgumentException(
					"지원하지 않는 이미지 형식입니다: " + file.getContentType()
							+ " (jpeg, png, webp, heic 만 업로드할 수 있습니다)");
		}

		String key = KEY_PREFIX + UUID.randomUUID() + "." + extension;
		if (!StringUtils.hasText(bucket)) {
			return MOCK_URL_PREFIX + key.substring(KEY_PREFIX.length());
		}
		return putObject(file, key);
	}

	private String putObject(MultipartFile file, String key) {
		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(file.getContentType())
				.build();
		try {
			s3ClientProvider.getObject().putObject(
					request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
		} catch (IOException | SdkException exception) {
			// SdkException 까지 잡는다 — 자격증명·권한·네트워크 실패가 그대로 새면 응답 형식이 제각각이 된다.
			throw new PhotoUploadFailedException("사진 업로드에 실패했습니다.", exception);
		}
		return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
	}

}
