package pt.diamondcars.dcbobackend.web.exception;

/**
 * Thrown by {@link pt.diamondcars.dcbobackend.config.ActiveUserInterceptor} when the authenticated
 * caller's local {@code app_users} profile has {@code active = false} (TASK-012, requirement 4) —
 * mapped by {@link pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a 403 response, in the
 * same {@code ApiError} envelope as every other mapped exception.
 */
public class InactiveUserException extends RuntimeException {

	/**
	 * Creates the exception with a human-readable message.
	 *
	 * @param message description to surface in the 403 response body's {@code message} field
	 */
	public InactiveUserException(String message) {
		super(message);
	}
}
