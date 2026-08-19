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

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청을 보낸 사용자를 세션 토큰에서 알아낸다.
 */
@Service
public class UserService {

	private static final String BEARER_PREFIX = "Bearer ";

	private static final Set<Integer> AGE_GROUPS = Set.of(10, 20, 30, 40, 50, 60);

	private final UserRepository userRepository;
	private final JwtService jwtService;

	public UserService(UserRepository userRepository, JwtService jwtService) {
		this.userRepository = userRepository;
		this.jwtService = jwtService;
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
	 * 신원은 세션 토큰(Authorization: Bearer)에서만 나온다.
	 *
	 * 예전에는 클라이언트가 헤더에 적어 보낸 UUID 를 그대로 믿었다. 그 UUID 를 아는 사람은
	 * 누구든 그 계정으로 행세할 수 있었으므로, 소셜 로그인 전환과 함께 걷어냈다.
	 */
	private UUID currentUserId() {
		HttpServletRequest request = currentRequest();
		String authorization = request == null ? null : request.getHeader(HttpHeaders.AUTHORIZATION);
		if (!StringUtils.hasText(authorization)) {
			throw new MissingUserSessionException("로그인이 필요합니다.");
		}
		if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			throw new InvalidTokenException("Authorization 헤더 형식이 올바르지 않습니다.");
		}
		return jwtService.verify(authorization.substring(BEARER_PREFIX.length()).trim());
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
				entity.getGender(),
				entity.getAgeGroup(),
				entity.getProfileAskedAt());
	}
}
