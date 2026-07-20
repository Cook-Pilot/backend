package com.cookpilot.backend.cooksession;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * cook_sessions 테이블 매핑(그룹 A). 조리 세션의 상태/진행을 보관한다.
 * setup_snapshot(JSONB)은 클라이언트 세션 셋업 스냅샷으로 opaque Map 매핑.
 */
@Entity
@Table(name = "cook_sessions")
public class CookSessionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	@Column(name = "personal_version_id")
	private UUID personalVersionId;

	@Convert(converter = SessionStatusConverter.class)
	@Column(name = "status", nullable = false)
	private SessionStatus status = SessionStatus.READY;

	@Column(name = "current_step_index", nullable = false)
	private int currentStepIndex = 0;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "aborted_at")
	private Instant abortedAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "setup_snapshot", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> setupSnapshot = new HashMap<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CookSessionEntity() {
	}

	public CookSessionEntity(UUID userId, UUID recipeId, UUID personalVersionId) {
		this.userId = userId;
		this.recipeId = recipeId;
		this.personalVersionId = personalVersionId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getRecipeId() {
		return recipeId;
	}

	public UUID getPersonalVersionId() {
		return personalVersionId;
	}

	public SessionStatus getStatus() {
		return status;
	}

	public void setStatus(SessionStatus status) {
		this.status = status;
	}

	public int getCurrentStepIndex() {
		return currentStepIndex;
	}

	public void setCurrentStepIndex(int currentStepIndex) {
		this.currentStepIndex = currentStepIndex;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public Instant getAbortedAt() {
		return abortedAt;
	}

	public void setAbortedAt(Instant abortedAt) {
		this.abortedAt = abortedAt;
	}

	public Map<String, Object> getSetupSnapshot() {
		return setupSnapshot;
	}

	public void setSetupSnapshot(Map<String, Object> setupSnapshot) {
		this.setupSnapshot = setupSnapshot;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
