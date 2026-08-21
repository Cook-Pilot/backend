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

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT user FROM UserEntity user WHERE user.id = :id")
	Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * 탈퇴 기록(tombstone). 백업 복원 후 삭제를 재적용하기 위한 목록이다(방침 제8조,
	 * infra/README 재해 복구 절차). ON CONFLICT 는 재시도 대비.
	 */
	@Modifying
	@Query(value = "INSERT INTO account_deletions (user_id) VALUES (:userId) ON CONFLICT DO NOTHING",
			nativeQuery = true)
	int recordDeletion(@Param("userId") UUID userId);
}
