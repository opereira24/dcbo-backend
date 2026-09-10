package pt.diamondcars.dcbobackend.web.dto;

import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;

/**
 * Consistent error response body for every 4xx raised by the API, per TASK-008 requirement 5. The
 * same shape is meant to be reused by every controller advice added by the tasks that follow
 * (TASK-009 to TASK-012), so clients only ever parse one error format.
 *
 * @param timestamp when the error was produced
 * @param status the HTTP status code, e.g. {@code 404}
 * @param error the HTTP status reason phrase, e.g. {@code "Not Found"}
 * @param message a human-readable description of what went wrong
 * @param path the request URI that caused the error
 */
public record ApiError(OffsetDateTime timestamp, int status, String error, String message, String path) {

	/**
	 * Builds an {@link ApiError} for the given status, filling {@link #timestamp} with the current
	 * instant and {@link #error} with the status's standard reason phrase.
	 *
	 * @param status the HTTP status to report
	 * @param message a human-readable description of what went wrong
	 * @param path the request URI that caused the error
	 * @return the resulting error body
	 */
	public static ApiError of(HttpStatus status, String message, String path) {
		return new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, path);
	}
}
