package com.cookpilot.backend.user;

import java.time.Instant;
import java.util.UUID;

public record User(
		UUID id,
		String email,
		String displayName,
		Gender gender,
		Integer ageGroup,
		Instant profileAskedAt
) {
}
