package com.cookpilot.backend.personalrecipe;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * personal_recipe_versions 테이블 매핑(그룹 A 구조).
 *
 * 조정값은 V2 부터 관계형 diff(personal_ingredient_adjustments / personal_step_adjustments)로
 * 저장한다. diff 는 항상 원본 레시피 기준 누적이므로, 이 버전의 렌더링은
 * "원본 + 이 버전의 diff 행"만으로 끝난다(부모 체인 재생 없음).
 */
@Entity
@Table(name = "personal_recipe_versions")
public class PersonalRecipeVersionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	@Column(name = "version_number", nullable = false)
	private int versionNumber;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "summary")
	private String summary;

	// 이 버전을 만든 리뷰(추적). 세션이 아니라 리뷰가 조리 1회의 기록이다.
	@Column(name = "source_review_id")
	private UUID sourceReviewId;

	// 진화 계보: 이 버전이 파생된 상위 버전(원본에서 바로 나왔으면 null).
	@Column(name = "parent_version_id")
	private UUID parentVersionId;

	@Column(name = "is_default", nullable = false)
	private boolean isDefault = false;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PersonalRecipeVersionEntity() {
	}

	public PersonalRecipeVersionEntity(UUID userId, UUID recipeId, int versionNumber, String title,
			String summary, UUID sourceReviewId) {
		this.userId = userId;
		this.recipeId = recipeId;
		this.versionNumber = versionNumber;
		this.title = title;
		this.summary = summary;
		this.sourceReviewId = sourceReviewId;
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

	public int getVersionNumber() {
		return versionNumber;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public UUID getSourceReviewId() {
		return sourceReviewId;
	}

	public UUID getParentVersionId() {
		return parentVersionId;
	}

	public void setParentVersionId(UUID parentVersionId) {
		this.parentVersionId = parentVersionId;
	}

	public boolean isDefault() {
		return isDefault;
	}

	public void setDefault(boolean isDefault) {
		this.isDefault = isDefault;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
