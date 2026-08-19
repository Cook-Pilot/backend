package com.cookpilot.backend.user;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.auth.AuthProvider;
import com.cookpilot.backend.auth.KakaoUnlinker;
import com.cookpilot.backend.review.ReviewPhotoService;

/**
 * 계정 삭제(#79). 방침 제9조 "계정과 모든 개인정보를 한 번에 삭제",
 * 제4조 "후기 본문과 사진 파일까지 함께 삭제, 익명 보존 없음"을 코드로 옮긴 것.
 *
 * 순서가 재시도 안전성을 만든다:
 * 1. S3 사진 삭제 — 실패하면 500. 계정이 그대로라 같은 호출을 다시 하면 된다.
 * 2. DB 행 삭제 + 탈퇴 기록 (한 트랜잭션, {@link UserService#deleteAccount}) — 실패해도
 *    사진만 먼저 사라진 상태라 재시도가 된다. 반대 순서(DB 먼저)면 실패 시점에 계정이
 *    이미 없어 재호출이 404 로 막히고, 사진이 영영 남는다.
 * 3. 카카오 unlink — best-effort. 외부 장애가 우리 삭제를 막으면 안 된다.
 *
 * 트랜잭션을 이 클래스에 걸지 않는 이유: 외부 호출(S3·카카오)을 DB 트랜잭션 안에 두면
 * 외부가 느릴 때 커넥션 풀이 마른다(AuthService 의 제공자 호출과 같은 원칙).
 *
 * 삭제 뒤에도 살아 있는 JWT(무상태, 최대 14일)는 getCurrentUser() 의 DB 조회가
 * UserNotFoundException(404) 으로 걸러낸다 — 같은 이유로 이 API 재호출도 404 라 멱등이다.
 */
@Service
public class AccountDeletionService {

	private final UserService userService;
	private final UserRepository userRepository;
	private final ReviewPhotoService reviewPhotoService;
	private final KakaoUnlinker kakaoUnlinker;

	public AccountDeletionService(
			UserService userService,
			UserRepository userRepository,
			ReviewPhotoService reviewPhotoService,
			KakaoUnlinker kakaoUnlinker) {
		this.userService = userService;
		this.userRepository = userRepository;
		this.reviewPhotoService = reviewPhotoService;
		this.kakaoUnlinker = kakaoUnlinker;
	}

	public void deleteCurrentAccount() {
		UUID userId = userService.getCurrentUser().id();
		// unlink 에 쓸 제공자 정보는 행이 사라지기 전에 읽어 둔다.
		UserEntity user = userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId));
		String provider = user.getProvider();
		String providerUserId = user.getProviderUserId();

		reviewPhotoService.deleteAllForUser(userId);
		userService.deleteAccount(userId);

		if (AuthProvider.KAKAO.name().equals(provider)) {
			kakaoUnlinker.unlink(providerUserId);
		}
	}
}
