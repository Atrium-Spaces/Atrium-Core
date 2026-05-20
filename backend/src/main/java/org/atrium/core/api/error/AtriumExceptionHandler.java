package org.atrium.core.api.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.time.Instant;
import java.util.Map;

/**
 * Translates exceptions into JSON error responses. Every response uses the same
 * envelope shape: {@code { timestamp, status, error, message }}.
 */
@Slf4j
@RestControllerAdvice
public final class AtriumExceptionHandler {

	@ExceptionHandler(AtriumException.class)
	public ResponseEntity<Map<String, Object>> handleAtrium(AtriumException e) {
		log.debug("Atrium exception {}: {}", e.status, e.getMessage());
		return ResponseEntity.status(e.status).body(body(e.status, e.getMessage()));
	}

	@ExceptionHandler(WebExchangeBindException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(WebExchangeBindException e) {
		return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, e.getFieldErrors().stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.reduce((left, right) -> left + "; " + right)
			.orElse("Validation failed")));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
		log.debug("Illegal argument: {}", e.getMessage());
		return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, e.getMessage()));
	}

	private Map<String, Object> body(HttpStatus status, String message) {
		return Map.of(
			"timestamp", Instant.now().toString(),
			"status", status.value(),
			"error", status.getReasonPhrase(),
			"message", message
		);
	}
}
