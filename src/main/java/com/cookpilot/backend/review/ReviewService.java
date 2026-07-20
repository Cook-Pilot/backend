package com.cookpilot.backend.review;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.cookpilot.backend.common.NotFoundException;
import com.cookpilot.backend.cooksession.CookSession;
import com.cookpilot.backend.cooksession.CookSessionService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeService;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersion;

/**
 * repository 계층 미확정. 조리 후 피드백을 인메모리로 저장하고,
 * 저장 시 개인 레시피 버전을 생성한다(조정값 구조화는 AI 미확정 - 목데이터).
 */
@Service
public class ReviewService {

	private final Map<UUID, PostCookReview> reviewsBySessionId = new ConcurrentHashMap<>();
	private final CookSessionService cookSessionService;
	private final PersonalRecipeService personalRecipeService;

	public ReviewService(CookSessionService cookSessionService, PersonalRecipeService personalRecipeService) {
		this.cookSessionService = cookSessionService;
		this.personalRecipeService = personalRecipeService;
	}

	public PostCookReview submit(UUID sessionId, int rating, String comment, String nextTimeNote) {
		if (rating < 1 || rating > 5) {
			throw new IllegalArgumentException("rating은 1~5 사이여야 합니다.");
		}
		CookSession session = cookSessionService.findById(sessionId);
		if (reviewsBySessionId.containsKey(sessionId)) {
			throw new IllegalStateException("이미 피드백이 저장된 세션입니다: " + sessionId);
		}
		cookSessionService.markCompleted(sessionId);

		PersonalRecipeVersion version = personalRecipeService.createFromReview(
				session.getRecipeId(), sessionId, comment, nextTimeNote);

		PostCookReview review = new PostCookReview(
				UUID.randomUUID(),
				sessionId,
				session.getUserId(),
				session.getRecipeId(),
				rating,
				comment,
				nextTimeNote,
				version.id(),
				Instant.now()
		);
		reviewsBySessionId.put(sessionId, review);
		return review;
	}

	public PostCookReview findBySessionId(UUID sessionId) {
		PostCookReview review = reviewsBySessionId.get(sessionId);
		if (review == null) {
			throw new NotFoundException("해당 세션의 피드백이 없습니다: " + sessionId);
		}
		return review;
	}
}
