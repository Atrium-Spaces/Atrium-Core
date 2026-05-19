package org.atrium.core.api.error;

import org.springframework.http.HttpStatus;

/**
 * Application-level lobby error. Carries an HTTP status so the
 * {@link LobbyExceptionHandler} can map it to a response without translating between
 * "domain" exceptions and Spring's web layer.
 */
public class LobbyException extends RuntimeException {

	private final HttpStatus status;

	public LobbyException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

	// ---- Common factories --------------------------------------------------------------------

	public static LobbyException roomNotFound(String code) {
		return new LobbyException(HttpStatus.NOT_FOUND, "Room not found: " + code);
	}

	public static LobbyException playerNotFound() {
		return new LobbyException(HttpStatus.NOT_FOUND, "Player not found");
	}

	public static LobbyException badCredentials() {
		return new LobbyException(HttpStatus.UNAUTHORIZED, "Public / secret id pair does not match");
	}

	public static LobbyException forbidden(String message) {
		return new LobbyException(HttpStatus.FORBIDDEN, message);
	}

	public static LobbyException conflict(String message) {
		return new LobbyException(HttpStatus.CONFLICT, message);
	}

	public static LobbyException badRequest(String message) {
		return new LobbyException(HttpStatus.BAD_REQUEST, message);
	}
}

