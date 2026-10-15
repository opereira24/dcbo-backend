package pt.diamondcars.dcbobackend.web.exception;

/**
 * Thrown when an authenticated admin calls {@code PATCH /api/users/{id}/active} with {@code
 * active: false} naming their own profile (TASK-012, requirement 1, fixed by {@code
 * backlog/reviews/TASK-012-r1.md}, IMPORTANTE 2) — mapped by {@link
 * pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a 409 response, same pattern as {@link
 * SelfDeletionException}.
 *
 * <p>Refusing this outright (rather than allowing it) avoids the case where the last authenticated
 * admin session deactivates itself and has no way back in: {@link
 * pt.diamondcars.dcbobackend.config.ActiveUserInterceptor} blocks every authenticated endpoint —
 * including this one, and {@code GET /api/me} — for a {@code sub} whose local profile is inactive,
 * so only another admin's session (or a manual database update, if there is none) can reactivate
 * it. Unlike {@link SelfDeletionException} (see its Javadoc), deleting one's own profile does
 * *not* trigger this lockout by itself — the interceptor only blocks a {@code sub} that still has
 * a local profile and it is inactive; a {@code sub} with no local profile at all is a separate
 * concern (see {@code backlog/reviews/TASK-012-r1.md}, "Nota para o planner" 1).
 */
public class SelfDeactivationException extends RuntimeException {

	/**
	 * Creates the exception with a human-readable message describing the conflict.
	 *
	 * @param message description to surface in the 409 response body's {@code message} field
	 */
	public SelfDeactivationException(String message) {
		super(message);
	}
}
