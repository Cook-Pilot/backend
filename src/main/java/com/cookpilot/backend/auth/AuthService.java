package com.cookpilot.backend.auth;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cookpilot.backend.user.UserEntity;
import com.cookpilot.backend.user.UserRepository;

/**
 * 소셜 토큰 → 우리 계정 → 세션 토큰.
 *
 * 계정 식별은 (provider, providerUserId) 로만 한다. 이메일로 찾지 않는다 — 이메일은 바뀔 수 있고,
 * 제공자가 안 줄 수도 있고, 무엇보다 "같은 이메일이면 같은 사람"이라는 가정은 계정 탈취 경로가 된다.
 */
@Service
public class AuthService {

	/** 개발자 로그인이 쓰는 고정 계정. 시크릿을 아는 사람은 모두 이 한 계정을 공유한다. */
	static final String DEV_PROVIDER = "DEV";
	static final String DEV_PROVIDER_USER_ID = "developer";

	private final Map<String, SocialVerifier> verifiers;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final String devLoginSecret;

	public AuthService(
			List<SocialVerifier> verifiers,
			UserRepository userRepository,
			JwtService jwtService,
			@Value("${cookpilot.auth.dev-login-secret:}") String devLoginSecret) {
		this.verifiers = verifiers.stream()
				.collect(java.util.stream.Collectors.toMap(SocialVerifier::provider, Function.identity()));
		this.userRepository = userRepository;
		this.jwtService = jwtService;
		this.devLoginSecret = devLoginSecret;
	}

	@Transactional
	public AuthResponse login(String provider, String token) {
		SocialVerifier verifier = verifiers.get(provider);
		if (verifier == null) {
			throw new IllegalArgumentException("지원하지 않는 로그인 제공자입니다: " + provider);
		}
		if (!StringUtils.hasText(token)) {
			throw new IllegalArgumentException("토큰이 비어 있습니다.");
		}
		return issueFor(verifier.verify(token));
	}

	/**
	 * 개발자 로그인. 클라이언트의 숨은 동작(로그인 화면 7번 탭)은 입구일 뿐 방어가 아니다 —
	 * 실제 방어는 이 시크릿이다. 설정하지 않으면 기능 자체가 꺼진다(운영에서 비활성 가능).
	 */
	@Transactional
	public AuthResponse loginAsDeveloper(String secret) {
		if (!StringUtils.hasText(devLoginSecret)) {
			throw new InvalidTokenException("개발자 로그인이 비활성화되어 있습니다.");
		}
		if (!java.security.MessageDigest.isEqual(
				devLoginSecret.getBytes(), String.valueOf(secret).getBytes())) {
			// 문자열 비교 대신 상수 시간 비교 — 응답 시간 차이로 시크릿을 한 글자씩 알아내는 걸 막는다.
			throw new InvalidTokenException("개발자 로그인 정보가 올바르지 않습니다.");
		}
		return issueFor(new SocialIdentity(DEV_PROVIDER, DEV_PROVIDER_USER_ID, null, "개발자"));
	}

	private AuthResponse issueFor(SocialIdentity identity) {
		UserEntity user = userRepository
				.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
				.orElseGet(() -> userRepository.save(UserEntity.ofSocial(
						identity.provider(),
						identity.providerUserId(),
						identity.email(),
						StringUtils.hasText(identity.displayName()) ? identity.displayName() : "쿡파일럿 사용자")));

		JwtService.IssuedToken issued = jwtService.issue(user.getId());
		return new AuthResponse(issued.token(), issued.expiresAt(), user.getId(), user.getDisplayName());
	}
}
