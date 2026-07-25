package com.cookpilot.backend.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.cookpilot.backend.common.NotFoundException;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 실제 인증 도입 전 폐쇄 베타용 사용자 식별을 담당한다.
 */
@Service
public class UserService {

	public static final String USER_ID_HEADER = "X-CookPilot-User-Id";

	private static final User DEMO_USER = new User(
			UUID.fromString("00000000-0000-0000-0000-000000000001"),
			"demo@cookpilot.app",
			"데모 사용자",
			0,
			false
	);

	private final UserRepository userRepository;
	private final EntityManager entityManager;

	public UserService(UserRepository userRepository, EntityManager entityManager) {
		this.userRepository = userRepository;
		this.entityManager = entityManager;
	}

	@Transactional
	public User createAnonymousUser() {
		UserEntity entity = userRepository.saveAndFlush(
				new UserEntity(null, "베타 사용자", true));

		// beta_number는 DB 시퀀스 기본값이므로 INSERT 뒤 다시 읽어 온다.
		entityManager.refresh(entity);
		entity.setDisplayName("베타 사용자 " + entity.getBetaNumber());
		return toUser(entity);
	}

	@Transactional(readOnly = true)
	public User getCurrentUser() {
		String userIdValue = currentRequestUserId();
		if (userIdValue == null || userIdValue.isBlank()) {
			// 기존 테스트와 데모 클라이언트 호환용. 베타 앱은 항상 발급받은 ID를 보낸다.
			return DEMO_USER;
		}

		UUID userId;
		try {
			userId = UUID.fromString(userIdValue);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("사용자 ID 형식이 올바르지 않습니다.");
		}

		return userRepository.findById(userId)
				.map(this::toUser)
				.orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + userId));
	}

	private String currentRequestUserId() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return null;
		}
		HttpServletRequest request = attributes.getRequest();
		return request.getHeader(USER_ID_HEADER);
	}

	private User toUser(UserEntity entity) {
		return new User(
				entity.getId(),
				entity.getEmail(),
				entity.getDisplayName(),
				entity.getBetaNumber(),
				entity.isAnonymous());
	}
}
