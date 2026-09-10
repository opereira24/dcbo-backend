package pt.diamondcars.dcbobackend.web.exception;

/**
 * Thrown when marking a car as featured ({@code destaque = true}) would exceed the maximum number
 * of simultaneously featured cars (TASK-008, requirement 1) — mapped by {@link
 * pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a 409 response.
 */
public class HighlightLimitExceededException extends RuntimeException {

	/**
	 * Creates the exception with a message stating the limit that was exceeded.
	 *
	 * @param limit the maximum number of cars allowed to be featured at the same time
	 */
	public HighlightLimitExceededException(int limit) {
		super("Limite de " + limit + " carros em destaque atingido");
	}
}
