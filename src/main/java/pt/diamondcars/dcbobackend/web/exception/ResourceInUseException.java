package pt.diamondcars.dcbobackend.web.exception;

/**
 * Thrown when deleting a resource (a client, a partner, ...) is refused because another resource
 * still depends on it — mapped by {@link pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a
 * 409 response (TASK-009, requirement 1 for clients / requirement 4 for partners).
 *
 * <p>Deleting the dependent instead of refusing the request would silently lose data (a car's
 * buyer or consignment partner), which is irreversible; an explicit 409 is not.
 */
public class ResourceInUseException extends RuntimeException {

	/**
	 * Creates the exception with a human-readable message describing the conflict.
	 *
	 * @param message description to surface in the 409 response body's {@code message} field
	 */
	public ResourceInUseException(String message) {
		super(message);
	}
}
