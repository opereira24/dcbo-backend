package pt.diamondcars.dcbobackend.web.exception;

/**
 * Thrown when {@link pt.diamondcars.dcbobackend.web.dto.TransactionRequest#data()} matches neither
 * format {@code dcbo} sends (TASK-011 requirement 7): a plain {@code "YYYY-MM-DD"} calendar date,
 * or a full ISO-8601 instant. Mapped by {@link pt.diamondcars.dcbobackend.web.ApiExceptionHandler}
 * to a 400 response.
 */
public class InvalidTransactionDateException extends RuntimeException {

	/**
	 * Creates the exception with a message naming the offending field and raw value.
	 *
	 * @param rawValue the value of {@code data} that could not be parsed
	 */
	public InvalidTransactionDateException(String rawValue) {
		super("data: valor invalido, esperado YYYY-MM-DD ou instante ISO-8601 (ex. 2026-03-31T23:30:00Z): " + rawValue);
	}
}
