package com.cookpilot.backend.user;

import java.util.UUID;

public record User(
		UUID id,
		String email,
		String displayName
) {
}
