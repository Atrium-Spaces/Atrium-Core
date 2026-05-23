package org.atrium.core.api.error;

import org.springframework.http.HttpStatus;

/**
 * Application-level error. Carries an HTTP status so the
 * {@link AtriumExceptionHandler} can map it to a response without translating between
 * "domain" exceptions and Spring's web layer.
 */
public class AtriumException extends RuntimeException {

	public final HttpStatus status;

	/**
	 * @param status  the HTTP status for the response
	 * @param message the human-readable error message
	 */
	public AtriumException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	/**
	 * Room with the given code does not exist.
	 */
	public static AtriumException roomNotFound(String code) {
		return new AtriumException(HttpStatus.NOT_FOUND, "Room not found: " + code);
	}

	/**
	 * Player with the given public id does not exist.
	 */
	public static AtriumException playerNotFound() {
		return new AtriumException(HttpStatus.NOT_FOUND, "Player not found");
	}

	/**
	 * The provided (publicId, secretId) pair does not match the stored record.
	 */
	public static AtriumException badCredentials() {
		return new AtriumException(HttpStatus.UNAUTHORIZED, "Public / secret id pair does not match");
	}

	/**
	 * The caller is not authorised for the requested operation (e.g. not the host).
	 */
	public static AtriumException forbidden(String message) {
		return new AtriumException(HttpStatus.FORBIDDEN, message);
	}

	/**
	 * The request conflicts with the current state (e.g. concurrent update, player already in a room).
	 */
	public static AtriumException conflict(String message) {
		return new AtriumException(HttpStatus.CONFLICT, message);
	}

	/**
	 * The request is structurally invalid (e.g. out-of-bounds player count).
	 */
	public static AtriumException badRequest(String message) {
		return new AtriumException(HttpStatus.BAD_REQUEST, message);
	}
}
