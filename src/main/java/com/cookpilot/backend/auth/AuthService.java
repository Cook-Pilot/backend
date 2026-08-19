package com.cookpilot.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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
	static final String DEV_PROVIDER_USER_ID = "developer";

	private final Map<AuthProvider, SocialVerifier> verifiers;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final byte[] devLoginSecret;

	public AuthService(
			List<SocialVerifier> verifiers,
			UserRepository userRepository,
			JwtService jwtService,
			@Value("${cookpilot.auth.dev-login-secret:}") String devLoginSecret) {
		this.verifiers = new EnumMap<>(AuthProvider.class);
		verifiers.forEach(verifier -> this.verifiers.put(verifier.provider(), verifier));
		this.userRepository = userRepository;
		this.jwtService = jwtService;
		// 플랫폼 기본 charset 에 맡기면 런타임 로케일에 따라 바이트가 달라진다.
		this.devLoginSecret = devLoginSecret.getBytes(StandardCharsets.UTF_8);
	}

	public AuthResponse login(AuthProvider provider, String token) {
		SocialVerifier verifier = verifiers.get(provider);
		if (verifier == null) {
			throw new IllegalArgumentException("지원하지 않는 로그인 제공자입니다: " + provider);
		}
		if (!StringUtils.hasText(token)) {
			throw new IllegalArgumentException("토큰이 비어 있습니다.");
		}
		// 제공자 호출은 트랜잭션 밖에서 한다 — 외부 응답을 기다리는 동안 DB 커넥션을 잡고 있으면
		// 제공자가 느릴 때 커넥션 풀이 마른다.
		return loginAs(verifier.verify(token));
	}

	/**
	 * 개발자 로그인. 클라이언트의 숨은 동작(로그인 화면 7번 탭)은 입구일 뿐 방어가 아니다 —
	 * 실제 방어는 이 시크릿이다. 설정하지 않으면 기능 자체가 꺼진다(운영에서 비활성 가능).
	 */
	public AuthResponse loginAsDeveloper(String secret) {
		if (devLoginSecret.length == 0) {
			throw new InvalidTokenException("개발자 로그인이 비활성화되어 있습니다.");
		}
		// 상수 시간 비교 — 응답 시간 차이로 시크릿을 한 글자씩 알아내는 걸 막는다.
		if (!MessageDigest.isEqual(
				devLoginSecret, String.valueOf(secret).getBytes(StandardCharsets.UTF_8))) {
			throw new InvalidTokenException("개발자 로그인 정보가 올바르지 않습니다.");
		}
		return loginAs(new SocialIdentity(AuthProvider.DEV, DEV_PROVIDER_USER_ID, null, "개발자"));
	}

	/** 확인된 신원으로 계정을 찾거나 만들고 세션 토큰을 발급한다. */
	private AuthResponse loginAs(SocialIdentity identity) {
		UserEntity user = findOrCreate(identity);
		JwtService.IssuedToken issued = jwtService.issue(user.getId());
		return new AuthResponse(issued.token(), issued.expiresAt(), user.getId(), user.getDisplayName());
	}

	/**
	 * (provider, providerUserId) 에 UNIQUE 인덱스가 있어, 같은 사람이 동시에 두 번 로그인하면
	 * 한쪽 INSERT 가 제약 위반으로 실패한다. 그대로 두면 로그인이 비결정적으로 500 이 되므로
	 * 충돌 시 상대가 만든 행을 다시 읽어 준다(결과적으로 멱등).
	 *
	 * 이 메서드에 트랜잭션을 걸지 않는다. 하나로 묶으면 제약 위반 시점에 그 트랜잭션이
	 * 롤백 전용이 되어 재조회조차 못 한다(REQUIRES_NEW 를 붙여도 같은 빈 내부 호출은
	 * 프록시를 타지 않아 무효다). 리포지토리 호출이 각각 자체 트랜잭션이라 실패한 INSERT 만
	 * 롤백되고 재조회는 새 트랜잭션에서 정상 수행된다.
	 */
	private UserEntity findOrCreate(SocialIdentity identity) {
		String provider = identity.provider().name();
		return userRepository.findByProviderAndProviderUserId(provider, identity.providerUserId())
				.orElseGet(() -> insertOrReread(identity, provider));
	}

	private UserEntity insertOrReread(SocialIdentity identity, String provider) {
		try {
			return userRepository.saveAndFlush(UserEntity.ofSocial(
					provider,
					identity.providerUserId(),
					identity.email(),
					StringUtils.hasText(identity.displayName()) ? identity.displayName() : "쿡파일럿 사용자"));
		} catch (DataIntegrityViolationException exception) {
			return userRepository.findByProviderAndProviderUserId(provider, identity.providerUserId())
					.orElseThrow(() -> exception);
		}
	}
}
