package com.cookpilot.backend.user;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.cookpilot.backend.auth.InvalidTokenException;
import com.cookpilot.backend.auth.JwtService;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 실제 인증 도입 전 폐쇄 베타용 사용자 식별을 담당한다.
 */
@Service
public class UserService {

	public static final String USER_ID_HEADER = "X-CookPilot-User-Id";

	private static final String BEARER_PREFIX = "Bearer ";
	public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

	private static final Set<String> GENDERS = Set.of("M", "F", "N");
	private static final Set<Integer> AGE_GROUPS = Set.of(10, 20, 30, 40, 50, 60);

	private final UserRepository userRepository;
	private final EntityManager entityManager;
	private final JwtService jwtService;

	public UserService(UserRepository userRepository, EntityManager entityManager, JwtService jwtService) {
		this.userRepository = userRepository;
		this.entityManager = entityManager;
		this.jwtService = jwtService;
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

	/**
	 * 온보딩 프로필 저장. 빈 body(건너뛰기)여도 profile_asked_at 은 찍는다 —
	 * 이 시각이 null 인 동안만 클라이언트가 온보딩을 띄운다.
	 */
	@Transactional
	public User updateProfile(UpdateProfileRequest request) {
		if (request.gender() != null && !GENDERS.contains(request.gender())) {
			throw new IllegalArgumentException("성별 값이 올바르지 않습니다: " + request.gender());
		}
		if (request.ageGroup() != null && !AGE_GROUPS.contains(request.ageGroup())) {
			throw new IllegalArgumentException("연령대 값이 올바르지 않습니다: " + request.ageGroup());
		}
		UUID userId = currentUserId();
		UserEntity entity = userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException(
						"사용자를 찾을 수 없습니다: " + userId));
		entity.applyProfile(request.gender(), request.ageGroup());
		return toUser(entity);
	}

	@Transactional
	public User lockCurrentUser() {
		UUID userId = currentUserId();
		return userRepository.findByIdForUpdate(userId)
				.map(this::toUser)
				.orElseThrow(() -> new UserNotFoundException(
						"사용자를 찾을 수 없습니다: " + userId));
	}

	/**
	 * 세션 토큰(Authorization: Bearer)을 우선 본다. 없으면 익명 발급 헤더로 떨어진다 —
	 * 프론트가 소셜 로그인으로 옮겨가는 동안 두 방식이 함께 살아 있어야 앱이 깨지지 않는다.
	 *
	 * TODO(소셜 로그인 전환 완료 후): 헤더 폴백과 익명 발급 API 제거.
	 */
	private UUID currentUserId() {
		HttpServletRequest request = currentRequest();
		if (request != null) {
			String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
			if (StringUtils.hasText(authorization)) {
				if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
					throw new InvalidTokenException("Authorization 헤더 형식이 올바르지 않습니다.");
				}
				return jwtService.verify(authorization.substring(BEARER_PREFIX.length()).trim());
			}
		}

		String userIdValue = request == null ? null : request.getHeader(USER_ID_HEADER);
		if (userIdValue == null || userIdValue.isBlank()) {
			throw new MissingUserSessionException("로그인이 필요합니다.");
		}
		try {
			return UUID.fromString(userIdValue);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("사용자 ID 형식이 올바르지 않습니다.");
		}
	}

	private HttpServletRequest currentRequest() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return null;
		}
		return attributes.getRequest();
	}

	private User toUser(UserEntity entity) {
		return new User(
				entity.getId(),
				entity.getEmail(),
				entity.getDisplayName(),
				entity.getBetaNumber(),
				entity.isAnonymous(),
				entity.getGender(),
				entity.getAgeGroup(),
				entity.getProfileAskedAt());
	}
}
