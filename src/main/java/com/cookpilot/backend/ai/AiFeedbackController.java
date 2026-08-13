package com.cookpilot.backend.ai;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/ai-feedback")
@RequiredArgsConstructor
public class AiFeedbackController {

	private final AiFeedbackService aiFeedbackService;

	@PostMapping
	public AiFeedbackResponse feedback(@Valid @RequestBody AiFeedbackRequest request) {
		return aiFeedbackService.feedback(request.recipeId(), request.stepIndex(), request.userSpeech());
	}
}
