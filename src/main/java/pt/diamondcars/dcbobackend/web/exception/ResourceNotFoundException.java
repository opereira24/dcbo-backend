package pt.diamondcars.dcbobackend.web.exception;

/**
 * Thrown by a service when a requested resource (a car, a client, ...) does not exist, and mapped
 * by {@link pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a 404 response.
 */
public class ResourceNotFoundException extends RuntimeException {

	/**
	 * Creates the exception with a human-readable message describing which resource was not found.
	 *
	 * @param message description to surface in the 404 response body's {@code message} field
	 */
	public ResourceNotFoundException(String message) {
		super(message);
	}
}
