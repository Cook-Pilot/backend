package com.cookpilot.backend.user;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * users 테이블 매핑. 순수 계정 도메인(AI 파트 무관, 그룹 A).
 */
@Entity
@Table(name = "users")
@Getter
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "email")
	private String email;

	/** 소셜 제공자(GOOGLE/KAKAO/APPLE/DEV). 익명 사용자는 null. */
	@Column(name = "provider")
	private String provider;

	/** 제공자가 부여한 계정 식별자. (provider, provider_user_id) 가 신원의 유일 키다. */
	@Column(name = "provider_user_id")
	private String providerUserId;

	@Setter
	@Column(name = "display_name")
	private String displayName;

	@Column(name = "beta_number", nullable = false, insertable = false, updatable = false)
	private Long betaNumber;

	@Column(name = "is_anonymous", nullable = false)
	private boolean anonymous;

	@Column(name = "anonymous_installation_id")
	private UUID anonymousInstallationId;

	/** 성별. 온보딩 선택 항목이라 null 허용. */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender")
	private Gender gender;

	/** 연령대(10~60, 60은 60세 이상). 온보딩 선택 항목이라 null 허용. */
	@Column(name = "age_group")
	private Integer ageGroup;

	/** 온보딩에서 물어본 시각. null 이면 아직 안 물어봤다 — 건너뛰어도 찍힌다. */
	@Column(name = "profile_asked_at")
	private Instant profileAskedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserEntity() {
	}

	public UserEntity(String email, String displayName) {
		this(email, displayName, false);
	}

	public UserEntity(String email, String displayName, boolean anonymous) {
		this(email, displayName, anonymous, null);
	}

	public UserEntity(
			String email,
			String displayName,
			boolean anonymous,
			UUID anonymousInstallationId) {
		this.email = email;
		this.displayName = displayName;
		this.anonymous = anonymous;
		this.anonymousInstallationId = anonymousInstallationId;
	}

	/** 소셜 로그인으로 만들어지는 계정. */
	public static UserEntity ofSocial(String provider, String providerUserId, String email, String displayName) {
		UserEntity entity = new UserEntity(email, displayName, false, null);
		entity.provider = provider;
		entity.providerUserId = providerUserId;
		return entity;
	}

	/** 온보딩 결과 반영. 값이 없어도(건너뛰기) 물어봤다는 사실은 기록한다. */
	public void applyProfile(Gender gender, Integer ageGroup) {
		if (gender != null) {
			this.gender = gender;
		}
		if (ageGroup != null) {
			this.ageGroup = ageGroup;
		}
		this.profileAskedAt = Instant.now();
	}

	/**
	 * 시퀀스가 채우는 컬럼이라 필드는 Long 이지만 조회 시점에는 항상 값이 있다.
	 * 기존 호출부 시그니처(long)를 유지하려고 @Getter 대신 직접 둔다.
	 */
	public long getBetaNumber() {
		return betaNumber;
	}
}
