package com.cookpilot.backend.ai;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cookpilot.backend.user.User;
import com.cookpilot.backend.user.UserService;

@RestController
@RequestMapping("/api/v1/ai-feedback")
public class AiFeedbackController {

	private static final int MAX_USER_SPEECH_LENGTH = 500;
	private static final int MAX_INSTRUCTION_LENGTH = 1_000;
	private static final int MAX_REMAINING_SECONDS = 86_400;

	private final AiFeedbackService aiFeedbackService;
	private final UserService userService;

	public AiFeedbackController(
			AiFeedbackService aiFeedbackService,
			UserService userService) {
		this.aiFeedbackService = aiFeedbackService;
		this.userService = userService;
	}

	/** 조리 컨텍스트(어떤 레시피의 몇 번째 단계인지)는 프론트가 들고 있으므로 요청으로 받는다. */
	@PostMapping
	public AiFeedbackResponse feedback(@RequestBody AiFeedbackRequest request) {
		if (request.recipeId() == null) {
			throw new IllegalArgumentException("recipeId는 필수입니다.");
		}
		if (request.stepIndex() == null) {
			throw new IllegalArgumentException("stepIndex는 필수입니다.");
		}
		if (request.stepIndex() < 0) {
			throw new IllegalArgumentException("stepIndex는 0 이상이어야 합니다.");
		}
		if (request.userSpeech() == null || request.userSpeech().isBlank()) {
			throw new IllegalArgumentException("userSpeech는 필수입니다.");
		}
		if (request.userSpeech().length() > MAX_USER_SPEECH_LENGTH) {
			throw new IllegalArgumentException(
					"userSpeech는 " + MAX_USER_SPEECH_LENGTH + "자 이하여야 합니다.");
		}
		if (request.instruction() != null
				&& (request.instruction().isBlank()
				|| request.instruction().length() > MAX_INSTRUCTION_LENGTH)) {
			throw new IllegalArgumentException(
					"instruction은 비어 있지 않은 "
							+ MAX_INSTRUCTION_LENGTH + "자 이하여야 합니다.");
		}
		if (request.remainingSeconds() != null
				&& (request.remainingSeconds() < 0
				|| request.remainingSeconds() > MAX_REMAINING_SECONDS)) {
			throw new IllegalArgumentException(
					"remainingSeconds는 0 이상 "
							+ MAX_REMAINING_SECONDS + " 이하여야 합니다.");
		}

		User user = userService.getCurrentUser();
		return aiFeedbackService.feedback(
				user.id(),
				request.recipeId(),
				request.stepIndex(),
				request.userSpeech(),
				request.instruction(),
				request.remainingSeconds());
	}

	/**
	 * instruction/remainingSeconds는 기존 클라이언트와 호환되는 선택 필드다.
	 * instruction이 없으면 서버 원본 단계 설명을 사용한다.
	 */
	public record AiFeedbackRequest(
			UUID recipeId,
			Integer stepIndex,
			String userSpeech,
			String instruction,
			Integer remainingSeconds
	) {
	}
}
