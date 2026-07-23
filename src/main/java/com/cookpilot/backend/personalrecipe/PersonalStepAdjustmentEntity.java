package com.cookpilot.backend.personalrecipe;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * personal_step_adjustments 테이블 매핑.
 *
 * 개인 버전 1개가 가진 단계 diff 행. ADD 는 insertAfterStepIndex(원본 몇 번 단계 뒤, -1 = 맨 앞)
 * 앵커로 끼워넣고, 같은 앵커 안에서는 sortOrder 순서를 따른다.
 * MODIFY 의 NULL 필드는 원본 값 유지("흔든다"→"젓는다"처럼 행위 자체가 바뀌어도 instruction
 * 오버라이드 한 건으로 표현된다).
 */
@Entity
@Table(name = "personal_step_adjustments")
public class PersonalStepAdjustmentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "personal_version_id", nullable = false)
	private UUID personalVersionId;

	@Column(name = "original_step_id")
	private UUID originalStepId;

	@Enumerated(EnumType.STRING)
	@Column(name = "adjustment_type", nullable = false)
	private AdjustmentType adjustmentType;

	@Column(name = "insert_after_step_index")
	private Integer insertAfterStepIndex;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder = 0;

	@Column(name = "instruction")
	private String instruction;

	@Column(name = "timer_seconds")
	private Integer timerSeconds;

	@Column(name = "caution_note")
	private String cautionNote;

	protected PersonalStepAdjustmentEntity() {
	}

	public PersonalStepAdjustmentEntity(UUID personalVersionId, UUID originalStepId,
			AdjustmentType adjustmentType, Integer insertAfterStepIndex, int sortOrder,
			String instruction, Integer timerSeconds, String cautionNote) {
		this.personalVersionId = personalVersionId;
		this.originalStepId = originalStepId;
		this.adjustmentType = adjustmentType;
		this.insertAfterStepIndex = insertAfterStepIndex;
		this.sortOrder = sortOrder;
		this.instruction = instruction;
		this.timerSeconds = timerSeconds;
		this.cautionNote = cautionNote;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPersonalVersionId() {
		return personalVersionId;
	}

	public UUID getOriginalStepId() {
		return originalStepId;
	}

	public AdjustmentType getAdjustmentType() {
		return adjustmentType;
	}

	public Integer getInsertAfterStepIndex() {
		return insertAfterStepIndex;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public String getInstruction() {
		return instruction;
	}

	public Integer getTimerSeconds() {
		return timerSeconds;
	}

	public String getCautionNote() {
		return cautionNote;
	}
}
