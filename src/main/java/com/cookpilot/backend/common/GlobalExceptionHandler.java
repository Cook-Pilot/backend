package com.cookpilot.backend.common;

import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.cookpilot.backend.auth.InvalidTokenException;
import com.cookpilot.backend.review.PhotoUploadFailedException;
import com.cookpilot.backend.user.MissingUserSessionException;
import com.cookpilot.backend.user.UserNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 단계 조정의 ADD/비ADD 조합 CHECK(V2). 서비스가 먼저 잡지만 새는 조합이 있으면 여기서 400. */
	private static final String STEP_ADJUSTMENT_CHECK = "personal_step_adjustments_check";

	/** 재료 조정의 ADD/비ADD 조합 CHECK(V2). */
	private static final String INGREDIENT_ADJUSTMENT_CHECK = "personal_ingredient_adjustments_check";

	@ExceptionHandler(UserNotFoundException.class)
	public ProblemDetail handleUserNotFound(UserNotFoundException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.NOT_FOUND, e.getMessage());
		problem.setProperty("code", "USER_NOT_FOUND");
		return problem;
	}

	@ExceptionHandler(MissingUserSessionException.class)
	public ProblemDetail handleMissingUserSession(MissingUserSessionException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.UNAUTHORIZED, e.getMessage());
		problem.setProperty("code", "USER_SESSION_REQUIRED");
		return problem;
	}

	/** 세션 토큰이 없거나 만료·위조. 클라이언트는 재로그인시켜야 한다. */
	@ExceptionHandler(InvalidTokenException.class)
	public ProblemDetail handleInvalidToken(InvalidTokenException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
		problem.setProperty("code", "INVALID_TOKEN");
		return problem;
	}

	@ExceptionHandler(NotFoundException.class)
	public ProblemDetail handleNotFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleBadRequest(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	/** @Valid 바인딩 검증 실패. 필드별 메시지를 모아 400 ProblemDetail 로 내린다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidationFailure(MethodArgumentNotValidException e) {
		String detail = e.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getDefaultMessage())
				.collect(Collectors.joining(" "));
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
	}

	@ExceptionHandler(IllegalStateException.class)
	public ProblemDetail handleConflict(IllegalStateException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	/** 업로드 파일이 spring.servlet.multipart 한도를 넘음. 안 잡으면 500 으로 나간다. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ProblemDetail handlePayloadTooLarge(MaxUploadSizeExceededException e) {
		return ProblemDetail.forStatusAndDetail(
				HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 큽니다. 한 장에 10MB 까지 올릴 수 있습니다.");
	}

	/** 저장소 장애. 클라이언트가 고칠 수 없으니 재시도 안내만 하고 원인은 로그로 남긴다. */
	@ExceptionHandler(PhotoUploadFailedException.class)
	public ProblemDetail handlePhotoUploadFailed(PhotoUploadFailedException e) {
		log.error("사진 업로드 실패", e);
		return ProblemDetail.forStatusAndDetail(
				HttpStatus.INTERNAL_SERVER_ERROR, "사진을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");
	}

	/**
	 * DB 제약 위반. 이름을 아는 제약만 4xx 로 옮기고 나머지는 500 그대로 둔다.
	 *
	 * 통째로 4xx 로 내리면 NOT NULL 누락·UNIQUE 충돌 같은 진짜 서버 버그가 클라 잘못으로
	 * 위장돼 로그에서 사라진다. 그래서 화이트리스트다 — 아는 이름이 아니면 스택과 함께 남긴다.
	 *
	 * 제약 이름은 Postgres 가 붙인 것이고 Hibernate 가 ConstraintViolationException 에 담아준다.
	 * 마이그레이션에서 제약 이름을 바꾸면 여기도 같이 바꿔야 한다.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException e) {
		String constraint = constraintName(e);
		if (STEP_ADJUSTMENT_CHECK.equals(constraint) || INGREDIENT_ADJUSTMENT_CHECK.equals(constraint)) {
			return ProblemDetail.forStatusAndDetail(
					HttpStatus.BAD_REQUEST, "저장할 수 없는 수정 조합입니다.");
		}
		log.error("처리하지 못한 제약 위반: constraint={}", constraint, e);
		return ProblemDetail.forStatusAndDetail(
				HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다.");
	}

	private String constraintName(DataIntegrityViolationException e) {
		return e.getCause() instanceof ConstraintViolationException violation
				? violation.getConstraintName()
				: null;
	}
}
