package com.cookpilot.backend.review;

/**
 * 조리 완료 저장과 후기 확정을 분리하는 상태.
 */
public enum ReviewLifecycleStatus {
	PENDING_REVIEW,
	FINALIZED,
	SKIPPED
}
