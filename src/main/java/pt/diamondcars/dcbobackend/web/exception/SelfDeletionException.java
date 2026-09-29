package pt.diamondcars.dcbobackend.web.exception;

/**
 * Thrown when an authenticated admin calls {@code DELETE /api/users/{id}} naming their own profile
 * (TASK-012, requirement 1 / acceptance criterion 6) — mapped by {@link
 * pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a 409 response.
 *
 * <p>Unlike {@link SelfDeactivationException}, deleting one's own profile does *not* lock the
 * caller out: {@link pt.diamondcars.dcbobackend.config.ActiveUserInterceptor} only blocks a {@code
 * sub} whose local profile still exists and is inactive, so a {@code sub} with no local profile at
 * all sails through it and keeps whatever access the Auth0 role claim in the JWT still grants (see
 * {@code backlog/reviews/TASK-012-r1.md}, "Nota para o planner" 1, and {@link
 * pt.diamondcars.dcbobackend.config.AuthenticatedUserProvider}). Refusing self-deletion is instead
 * about parity with {@code dcbo/src/pages/users.js:180} and avoiding an admin accidentally erasing
 * their own profile/history — not about a lockout.
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
