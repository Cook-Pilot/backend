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
