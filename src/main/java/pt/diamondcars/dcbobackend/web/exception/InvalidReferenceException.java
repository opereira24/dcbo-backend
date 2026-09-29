package pt.diamondcars.dcbobackend.web.exception;

/**
 * Thrown when a request body references another resource by id (e.g. {@code carroId}) that does
 * not exist, for the one relation TASK-011 requirement 2 explicitly asks to reject with 400 rather
 * than 404 — unlike the 404 precedent {@link pt.diamondcars.dcbobackend.web.dto.CarRequest} ({@code
 * partnerId})/{@link pt.diamondcars.dcbobackend.web.dto.LeadRequest} ({@code carroId}) set for the
 * same "unknown referenced id" shape elsewhere in this API. Mapped by {@link
 * pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a 400 response.
 */
public class InvalidReferenceException extends RuntimeException {

	/**
	 * Creates the exception with a human-readable message naming the offending field/id.
	 *
	 * @param message description to surface in the 400 response body's {@code message} field
	 */
	public InvalidReferenceException(String message) {
		super(message);
	}
}
