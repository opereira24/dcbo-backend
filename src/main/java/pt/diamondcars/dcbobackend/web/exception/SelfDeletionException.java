package pt.diamondcars.dcbobackend.web.exception;

/**
 * Thrown when an authenticated admin calls {@code DELETE /api/users/{id}} naming their own profile
 * (TASK-012, requirement 1 / acceptance criterion 6) — mapped by {@link
 * pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a 409 response. Refusing this outright
 * (rather than allowing it) avoids the case where the last authenticated admin session locks
 * itself out of every other admin-only endpoint with no local profile left to re-grant access.
 */
public class SelfDeletionException extends RuntimeException {

	/**
	 * Creates the exception with a human-readable message describing the conflict.
	 *
	 * @param message description to surface in the 409 response body's {@code message} field
	 */
	public SelfDeletionException(String message) {
		super(message);
	}
}
