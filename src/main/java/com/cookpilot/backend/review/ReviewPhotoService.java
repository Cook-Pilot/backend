package com.cookpilot.backend.review;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cookpilot.backend.user.UserService;

/**
 * 리뷰 사진 업로드. 스토리지(S3)가 아직 없어 파일을 저장하지 않고 목 URL만 돌려준다.
 * 클라이언트는 이 URL을 받아 {@code POST /reviews} 의 photoUrls 에 넣는 흐름을 지금부터 태울 수 있다.
 *
 * 여러 장은 이 업로드를 장수만큼 반복한다 — 장당 한 요청이라 실패한 장만 재시도하면 된다.
 *
 * 다른 개인화 API 와 동일하게 베타 사용자 세션을 요구한다. 실제 스토리지가 붙으면
 * 업로드 비용이 발생하므로 무인증 호출을 지금부터 막아 프론트 계약도 인증 포함으로 고정한다.
 *
 * TODO(S3 확정 후): 실제 업로드로 교체. 버킷/키 규칙, URL 도메인 확정.
 */
@Service
public class ReviewPhotoService {

	static final String MOCK_URL_PREFIX = "https://mock-storage.cookpilot.local/review-photos/";

	private final UserService userService;

	public ReviewPhotoService(UserService userService) {
		this.userService = userService;
	}

	public String upload(MultipartFile file) {
		userService.getCurrentUser();
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("파일이 비어 있습니다.");
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
		}
		return MOCK_URL_PREFIX + UUID.randomUUID();
	}

}
