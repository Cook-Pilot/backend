package com.cookpilot.backend.cooksession;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CookSessionEvent(
		UUID id,
		UUID cookSessionId,
		String eventType,
		Integer stepIndex,
		String source,
		Map<String, Object> payload,
		Instant createdAt
) {
}
