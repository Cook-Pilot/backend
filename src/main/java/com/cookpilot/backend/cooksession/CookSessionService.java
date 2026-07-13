package com.cookpilot.backend.cooksession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.personalrecipe.PersonalRecipeService;
import com.cookpilot.backend.recipe.Recipe;
import com.cookpilot.backend.recipe.RecipeService;
import com.cookpilot.backend.user.UserService;

/**
 * repository 계층 미확정. 조리 세션을 인메모리로 저장한다.
 * 타이머는 클라이언트 로컬에서 진행하고, 서버는 타이머 이벤트만 기록한다.
 */
@Service
public class CookSessionService {

	private final Map<UUID, CookSession> sessions = new ConcurrentHashMap<>();
	private final RecipeService recipeService;
	private final PersonalRecipeService personalRecipeService;
	private final UserService userService;

	public CookSessionService(RecipeService recipeService, PersonalRecipeService personalRecipeService,
			UserService userService) {
		this.recipeService = recipeService;
		this.personalRecipeService = personalRecipeService;
		this.userService = userService;
	}

	public CookSession create(UUID recipeId, UUID personalVersionId) {
		Recipe recipe = recipeService.findById(recipeId);
		if (personalVersionId != null) {
			// 존재 검증만 수행. 조정값을 단계 스냅샷에 반영하는 규칙은 AI 파트 확정 후 구현.
			personalRecipeService.findById(personalVersionId);
		}
		CookSession session = new CookSession(
				UUID.randomUUID(),
				userService.getCurrentUser().id(),
				recipeId,
				personalVersionId,
				recipe.title(),
				recipe.steps()
		);
		sessions.put(session.getId(), session);
		addEvent(session.getId(), "SESSION_STARTED", 0, "SYSTEM", Map.of());
		return session;
	}

	public CookSession findById(UUID sessionId) {
		CookSession session = sessions.get(sessionId);
		if (session == null) {
			throw new NotFoundException("조리 세션을 찾을 수 없습니다: " + sessionId);
		}
		return session;
	}

	public CookSession moveStep(UUID sessionId, StepDirection direction, String source) {
		CookSession session = findById(sessionId);
		requireActive(session);

		int nextIndex = session.getCurrentStepIndex() + (direction == StepDirection.NEXT ? 1 : -1);
		if (nextIndex < 0 || nextIndex >= session.getStepSnapshot().size()) {
			throw new IllegalArgumentException("이동할 수 없는 단계입니다: " + nextIndex);
		}
		session.setCurrentStepIndex(nextIndex);
		addEvent(sessionId, "STEP_MOVED", nextIndex, source, Map.of("direction", direction.name()));
		return session;
	}

	public CookSession complete(UUID sessionId) {
		CookSession session = findById(sessionId);
		requireActive(session);
		session.setStatus(SessionStatus.REVIEW);
		session.setCompletedAt(Instant.now());
		addEvent(sessionId, "SESSION_COMPLETED", session.getCurrentStepIndex(), "USER", Map.of());
		return session;
	}

	public CookSession abort(UUID sessionId) {
		CookSession session = findById(sessionId);
		requireActive(session);
		session.setStatus(SessionStatus.ABORTED);
		session.setAbortedAt(Instant.now());
		addEvent(sessionId, "SESSION_ABORTED", session.getCurrentStepIndex(), "USER", Map.of());
		return session;
	}

	public void markCompleted(UUID sessionId) {
		CookSession session = findById(sessionId);
		if (session.getStatus() != SessionStatus.REVIEW) {
			throw new IllegalStateException("리뷰 대기 상태가 아닌 세션입니다: " + session.getStatus());
		}
		session.setStatus(SessionStatus.COMPLETED);
	}

	public CookSessionEvent addEvent(UUID sessionId, String eventType, Integer stepIndex, String source,
			Map<String, Object> payload) {
		CookSession session = findById(sessionId);
		CookSessionEvent event = new CookSessionEvent(
				UUID.randomUUID(),
				sessionId,
				eventType,
				stepIndex,
				source,
				payload == null ? Map.of() : payload,
				Instant.now()
		);
		synchronized (session.getEvents()) {
			session.getEvents().add(event);
		}
		return event;
	}

	public List<CookSessionEvent> findEvents(UUID sessionId) {
		CookSession session = findById(sessionId);
		synchronized (session.getEvents()) {
			return List.copyOf(session.getEvents());
		}
	}

	private void requireActive(CookSession session) {
		if (session.getStatus() != SessionStatus.COOKING && session.getStatus() != SessionStatus.PAUSED) {
			throw new IllegalStateException("진행 중인 세션이 아닙니다: " + session.getStatus());
		}
	}
}
