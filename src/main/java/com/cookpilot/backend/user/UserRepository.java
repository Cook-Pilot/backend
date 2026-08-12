package com.cookpilot.backend.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

	Optional<UserEntity> findByEmail(String email);

	/** 소셜 계정 식별. 이메일이 아니라 이 조합이 신원의 유일 키다. */
	Optional<UserEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

	Optional<UserEntity> findByAnonymousInstallationId(UUID anonymousInstallationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT user FROM UserEntity user WHERE user.id = :id")
	Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);

	@Modifying
	@Query(value = """
			INSERT INTO users (
				id, email, display_name, is_anonymous, anonymous_installation_id
			)
			VALUES (:id, NULL, '베타 사용자', TRUE, :installationId)
			ON CONFLICT DO NOTHING
			""", nativeQuery = true)
	int insertAnonymousIgnore(
			@Param("id") UUID id,
			@Param("installationId") UUID installationId);
}
