package pt.diamondcars.dcbobackend.web.exception;

/**
 * Thrown when {@code POST /api/users} is called with an {@code authSubject} a profile already
 * exists for (TASK-012, requirement 1) — mapped by {@link
 * pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a 409 response, instead of letting the
 * database's unique index on {@code app_users.auth_subject} (TASK-005 acceptance criterion 5)
 * surface as an unmapped {@link org.springframework.dao.DataIntegrityViolationException}.
 */
public class DuplicateAuthSubjectException extends RuntimeException {

	/**
	 * Creates the exception with a human-readable message describing the conflict.
	 *
	 * @param message description to surface in the 409 response body's {@code message} field
	 */
	public DuplicateAuthSubjectException(String message) {
		super(message);
	}
}
