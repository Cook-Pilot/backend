package com.cookpilot.backend.ai;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai-feedback")
public class AiFeedbackController {

	private final AiFeedbackService aiFeedbackService;

	public AiFeedbackController(AiFeedbackService aiFeedbackService) {
		this.aiFeedbackService = aiFeedbackService;
	}

	@PostMapping
	public AiFeedbackResponse feedback(@Valid @RequestBody AiFeedbackRequest request) {
		return aiFeedbackService.feedback(request.recipeId(), request.stepIndex(), request.userSpeech());
	}
}
