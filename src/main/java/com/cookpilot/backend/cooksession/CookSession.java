package com.cookpilot.backend.cooksession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cookpilot.backend.recipe.RecipeStep;

public class CookSession {

	private final UUID id;
	private final UUID userId;
	private final UUID recipeId;
	private final UUID personalVersionId;
	private final String recipeTitle;
	private final List<RecipeStep> stepSnapshot;
	private final Instant startedAt;
	private final List<CookSessionEvent> events = new ArrayList<>();

	private SessionStatus status;
	private int currentStepIndex;
	private Instant completedAt;
	private Instant abortedAt;

	public CookSession(UUID id, UUID userId, UUID recipeId, UUID personalVersionId, String recipeTitle,
			List<RecipeStep> stepSnapshot) {
		this.id = id;
		this.userId = userId;
		this.recipeId = recipeId;
		this.personalVersionId = personalVersionId;
		this.recipeTitle = recipeTitle;
		this.stepSnapshot = List.copyOf(stepSnapshot);
		this.startedAt = Instant.now();
		this.status = SessionStatus.COOKING;
		this.currentStepIndex = 0;
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

	public String getRecipeTitle() {
		return recipeTitle;
	}

	public List<RecipeStep> getStepSnapshot() {
		return stepSnapshot;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public List<CookSessionEvent> getEvents() {
		return events;
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

	public RecipeStep currentStep() {
		return stepSnapshot.get(currentStepIndex);
	}
}
