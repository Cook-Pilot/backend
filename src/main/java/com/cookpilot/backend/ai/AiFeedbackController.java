package com.cookpilot.backend.ai;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-feedback")
public class AiFeedbackController {

	private final AiFeedbackService aiFeedbackService;

	public AiFeedbackController(AiFeedbackService aiFeedbackService) {
		this.aiFeedbackService = aiFeedbackService;
	}

	@PostMapping
	public AiFeedbackResponse feedback(@RequestBody AiFeedbackRequest request) {
		if (request.recipeId() == null) {
			throw new IllegalArgumentException("recipeId는 필수입니다.");
		}
		if (request.stepIndex() == null) {
			throw new IllegalArgumentException("stepIndex는 필수입니다.");
		}
		if (request.userSpeech() == null || request.userSpeech().isBlank()) {
			throw new IllegalArgumentException("userSpeech는 필수입니다.");
		}
		return aiFeedbackService.feedback(request.recipeId(), request.stepIndex(), request.userSpeech());
	}
}
