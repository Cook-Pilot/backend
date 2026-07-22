package com.cookpilot.backend.recipe;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * recipe_steps 테이블 매핑(그룹 A). (recipe_id, step_index) 유니크.
 */
@Entity
@Table(name = "recipe_steps")
public class RecipeStepEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	@Column(name = "step_index", nullable = false)
	private int stepIndex;

	@Column(name = "instruction", nullable = false)
	private String instruction;

	@Column(name = "timer_seconds")
	private Integer timerSeconds;

	@Column(name = "caution_note")
	private String cautionNote;

	/** 단계 참고 이미지 URL. 원본은 외부 스토리지에 두고 여기엔 URL만 둔다(NULL = 이미지 없음). */
	@Column(name = "image_url")
	private String imageUrl;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RecipeStepEntity() {
	}

	public RecipeStepEntity(UUID recipeId, int stepIndex, String instruction, Integer timerSeconds,
			String cautionNote) {
		this(recipeId, stepIndex, instruction, timerSeconds, cautionNote, null);
	}

	public RecipeStepEntity(UUID recipeId, int stepIndex, String instruction, Integer timerSeconds,
			String cautionNote, String imageUrl) {
		this.recipeId = recipeId;
		this.stepIndex = stepIndex;
		this.instruction = instruction;
		this.timerSeconds = timerSeconds;
		this.cautionNote = cautionNote;
		this.imageUrl = imageUrl;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRecipeId() {
		return recipeId;
	}

	public int getStepIndex() {
		return stepIndex;
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

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
