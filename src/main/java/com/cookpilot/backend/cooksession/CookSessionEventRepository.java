package com.cookpilot.backend.cooksession;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CookSessionEventRepository extends JpaRepository<CookSessionEventEntity, UUID> {

	List<CookSessionEventEntity> findByCookSessionIdOrderByCreatedAtAsc(UUID cookSessionId);
}
