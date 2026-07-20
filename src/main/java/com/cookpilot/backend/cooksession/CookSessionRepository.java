package com.cookpilot.backend.cooksession;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CookSessionRepository extends JpaRepository<CookSessionEntity, UUID> {

	List<CookSessionEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
