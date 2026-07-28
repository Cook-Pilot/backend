package com.cookpilot.backend.user;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

	@Column(name = "email", unique = true)
	private String email;

	@Setter
	@Column(name = "display_name")
	private String displayName;

	@Column(name = "beta_number", nullable = false, insertable = false, updatable = false)
	private Long betaNumber;

	@Column(name = "is_anonymous", nullable = false)
	private boolean anonymous;

	@Column(name = "anonymous_installation_id")
	private UUID anonymousInstallationId;

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

	/**
	 * 시퀀스가 채우는 컬럼이라 필드는 Long 이지만 조회 시점에는 항상 값이 있다.
	 * 기존 호출부 시그니처(long)를 유지하려고 @Getter 대신 직접 둔다.
	 */
	public long getBetaNumber() {
		return betaNumber;
	}
}
