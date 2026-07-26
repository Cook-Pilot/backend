package com.cookpilot.backend.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 실제 인증 도입 전 폐쇄 베타용 사용자 식별을 담당한다.
 */
@Service
public class UserService {

	public static final String USER_ID_HEADER = "X-CookPilot-User-Id";
	public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

	private final UserRepository userRepository;
	private final EntityManager entityManager;

	public UserService(UserRepository userRepository, EntityManager entityManager) {
		this.userRepository = userRepository;
		this.entityManager = entityManager;
	}

	@Transactional
	public User createAnonymousUser(String idempotencyKey) {
		UUID installationId = parseInstallationId(idempotencyKey);
		if (installationId != null) {
			int inserted = userRepository.insertAnonymousIgnore(UUID.randomUUID(), installationId);
			UserEntity entity = userRepository.findByAnonymousInstallationId(installationId)
					.orElseThrow(() -> new IllegalStateException("익명 사용자를 조회하지 못했습니다."));
			if (inserted == 1) {
				entity.setDisplayName("베타 사용자 " + entity.getBetaNumber());
			}
			return toUser(entity);
		}

		UserEntity entity = userRepository.saveAndFlush(
				new UserEntity(null, "베타 사용자", true, installationId));

		// beta_number는 DB 시퀀스 기본값이므로 INSERT 뒤 다시 읽어 온다.
		entityManager.refresh(entity);
		entity.setDisplayName("베타 사용자 " + entity.getBetaNumber());
		return toUser(entity);
	}

	private UUID parseInstallationId(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			// 기존 데모 클라이언트와 테스트는 키 없이도 매번 새 사용자를 만들 수 있다.
			return null;
		}
		try {
			return UUID.fromString(idempotencyKey);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("익명 사용자 생성 키 형식이 올바르지 않습니다.");
		}
	}

	@Transactional(readOnly = true)
	public User getCurrentUser() {
		UUID userId = currentUserId();
		return userRepository.findById(userId)
				.map(this::toUser)
				.orElseThrow(() -> new UserNotFoundException(
						"사용자를 찾을 수 없습니다: " + userId));
	}

	@Transactional
	public User lockCurrentUser() {
		UUID userId = currentUserId();
		return userRepository.findByIdForUpdate(userId)
				.map(this::toUser)
				.orElseThrow(() -> new UserNotFoundException(
						"사용자를 찾을 수 없습니다: " + userId));
	}

	private UUID currentUserId() {
		String userIdValue = currentRequestUserId();
		if (userIdValue == null || userIdValue.isBlank()) {
			throw new MissingUserSessionException("베타 사용자 세션이 필요합니다.");
		}
		try {
			return UUID.fromString(userIdValue);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("사용자 ID 형식이 올바르지 않습니다.");
		}
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
