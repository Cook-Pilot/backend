package com.cookpilot.backend.cooksession;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cook-sessions")
public class CookSessionController {

	private final CookSessionService cookSessionService;

	public CookSessionController(CookSessionService cookSessionService) {
		this.cookSessionService = cookSessionService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CookSessionResponse create(@RequestBody CreateSessionRequest request) {
		if (request.recipeId() == null) {
			throw new IllegalArgumentException("recipeId는 필수입니다.");
		}
		return CookSessionResponse.from(cookSessionService.create(request.recipeId(), request.personalVersionId()));
	}

	@GetMapping("/{sessionId}")
	public CookSessionResponse get(@PathVariable UUID sessionId) {
		return CookSessionResponse.from(cookSessionService.findById(sessionId));
	}

	@PostMapping("/{sessionId}/step")
	public CookSessionResponse moveStep(@PathVariable UUID sessionId, @RequestBody MoveStepRequest request) {
		if (request.direction() == null) {
			throw new IllegalArgumentException("direction은 필수입니다. (NEXT | PREV)");
		}
		String source = request.source() == null ? "BUTTON" : request.source();
		return CookSessionResponse.from(cookSessionService.moveStep(sessionId, request.direction(), source));
	}

	@PostMapping("/{sessionId}/complete")
	public CookSessionResponse complete(@PathVariable UUID sessionId) {
		return CookSessionResponse.from(cookSessionService.complete(sessionId));
	}

	@PostMapping("/{sessionId}/abort")
	public CookSessionResponse abort(@PathVariable UUID sessionId) {
		return CookSessionResponse.from(cookSessionService.abort(sessionId));
	}

	@PostMapping("/{sessionId}/events")
	@ResponseStatus(HttpStatus.CREATED)
	public CookSessionEvent addEvent(@PathVariable UUID sessionId, @RequestBody CreateEventRequest request) {
		if (request.eventType() == null || request.eventType().isBlank()) {
			throw new IllegalArgumentException("eventType은 필수입니다.");
		}
		String source = request.source() == null ? "CLIENT" : request.source();
		return cookSessionService.addEvent(sessionId, request.eventType(), request.stepIndex(), source,
				request.payload());
	}

	@GetMapping("/{sessionId}/events")
	public List<CookSessionEvent> listEvents(@PathVariable UUID sessionId) {
		return cookSessionService.findEvents(sessionId);
	}

	public record CreateSessionRequest(UUID recipeId, UUID personalVersionId) {
	}

	public record MoveStepRequest(StepDirection direction, String source) {
	}

	public record CreateEventRequest(String eventType, Integer stepIndex, String source, Map<String, Object> payload) {
	}
}
