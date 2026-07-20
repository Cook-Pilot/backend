package com.cookpilot.backend.cooksession;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * cook_session_events 테이블 매핑(그룹 A). 세션 이벤트 append-only 로그.
 * payload(JSONB)는 이벤트 유형별로 자유로운 부가정보라 opaque Map 으로 둔다.
 */
@Entity
@Table(name = "cook_session_events")
public class CookSessionEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "cook_session_id", nullable = false)
	private UUID cookSessionId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@Column(name = "step_index")
	private Integer stepIndex;

	@Column(name = "source", nullable = false)
	private String source;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload = new HashMap<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected CookSessionEventEntity() {
	}

	public CookSessionEventEntity(UUID cookSessionId, String eventType, Integer stepIndex, String source,
			Map<String, Object> payload) {
		this.cookSessionId = cookSessionId;
		this.eventType = eventType;
		this.stepIndex = stepIndex;
		this.source = source;
		if (payload != null) {
			this.payload = payload;
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getCookSessionId() {
		return cookSessionId;
	}

	public String getEventType() {
		return eventType;
	}

	public Integer getStepIndex() {
		return stepIndex;
	}

	public String getSource() {
		return source;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
