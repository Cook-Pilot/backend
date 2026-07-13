package com.cookpilot.backend.ai;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cook-sessions/{sessionId}/ai-feedback")
public class AiFeedbackController {

	private final AiFeedbackService aiFeedbackService;

	public AiFeedbackController(AiFeedbackService aiFeedbackService) {
		this.aiFeedbackService = aiFeedbackService;
	}

	@PostMapping
	public AiFeedbackResponse feedback(@PathVariable UUID sessionId, @RequestBody AiFeedbackRequest request) {
		if (request.userSpeech() == null || request.userSpeech().isBlank()) {
			throw new IllegalArgumentException("userSpeech는 필수입니다.");
		}
		return aiFeedbackService.feedback(sessionId, request.userSpeech());
	}

	public record AiFeedbackRequest(String userSpeech) {
	}
}
