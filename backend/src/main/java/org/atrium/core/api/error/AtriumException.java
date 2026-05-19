package org.atrium.core.api.error;

import org.springframework.http.HttpStatus;

/**
 * Application-level error. Carries an HTTP status so the
 * {@link AtriumExceptionHandler} can map it to a response without translating between
 * "domain" exceptions and Spring's web layer.
 */
public class AtriumException extends RuntimeException {

	public final HttpStatus status;

	public AtriumException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public static AtriumException roomNotFound(String code) {
		return new AtriumException(HttpStatus.NOT_FOUND, "Room not found: " + code);
	}

	public static AtriumException playerNotFound() {
		return new AtriumException(HttpStatus.NOT_FOUND, "Player not found");
	}

	public static AtriumException badCredentials() {
		return new AtriumException(HttpStatus.UNAUTHORIZED, "Public / secret id pair does not match");
	}

	public static AtriumException forbidden(String message) {
		return new AtriumException(HttpStatus.FORBIDDEN, message);
	}

	public static AtriumException conflict(String message) {
		return new AtriumException(HttpStatus.CONFLICT, message);
	}

	public static AtriumException badRequest(String message) {
		return new AtriumException(HttpStatus.BAD_REQUEST, message);
	}
}
