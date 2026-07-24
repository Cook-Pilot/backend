package com.cookpilot.api;

import com.cookpilot.api.ApiModels.ErrorResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    Map<String, String> fields = new LinkedHashMap<>();
    exception.getBindingResult().getFieldErrors().forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
    return ResponseEntity.unprocessableEntity()
        .body(new ErrorResponse("VALIDATION_FAILED", message, requestId(), false, fields, Instant.now()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ErrorResponse> status(ResponseStatusException exception) {
    String code = codeFor(exception.getStatusCode().value());
    boolean retryable = exception.getStatusCode().is5xxServerError();
    return ResponseEntity.status(exception.getStatusCode())
        .body(
            new ErrorResponse(
                code,
                exception.getReason() == null ? "요청을 처리할 수 없습니다." : exception.getReason(),
                requestId(),
                retryable,
                Map.of(),
                Instant.now()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception exception) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            new ErrorResponse(
                "INTERNAL_ERROR",
                "서버에서 요청을 처리하지 못했습니다.",
                requestId(),
                true,
                Map.of(),
                Instant.now()));
  }

  private String requestId() {
    return UUID.randomUUID().toString();
  }

  private String codeFor(int status) {
    return switch (status) {
      case 401 -> "UNAUTHORIZED";
      case 404 -> "NOT_FOUND";
      case 409 -> "INVALID_STATE";
      case 422 -> "VALIDATION_FAILED";
      default -> "REQUEST_ERROR";
    };
  }
}
