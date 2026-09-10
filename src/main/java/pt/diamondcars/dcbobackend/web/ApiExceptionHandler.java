package pt.diamondcars.dcbobackend.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pt.diamondcars.dcbobackend.web.dto.ApiError;
import pt.diamondcars.dcbobackend.web.exception.CarAlreadySoldException;
import pt.diamondcars.dcbobackend.web.exception.HighlightLimitExceededException;
import pt.diamondcars.dcbobackend.web.exception.ResourceNotFoundException;

/**
 * Central exception-to-HTTP-response translation for the whole API, per TASK-008 requirement 5.
 * Produces the same {@link ApiError} shape ({@code timestamp}, {@code status}, {@code error},
 * {@code message}, {@code path}) for every mapped exception, so clients only ever parse one error
 * format. Shared by every business endpoint added by the tasks that follow (TASK-009 to
 * TASK-012): a new exception type in those tasks is mapped here by adding one more {@code
 * @ExceptionHandler} method, never by duplicating this class.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	/**
	 * Maps {@link ResourceNotFoundException} (a car, client, ... that does not exist) to 404.
	 *
	 * @param exception the exception thrown by the service layer
	 * @param request the failed request, used to report {@link ApiError#path()}
	 * @return the 404 response body
	 */
	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(
			ResourceNotFoundException exception, HttpServletRequest request) {
		return respond(HttpStatus.NOT_FOUND, exception.getMessage(), request);
	}

	/**
	 * Maps {@link HighlightLimitExceededException} (the 8-featured-cars limit, TASK-008 requirement
	 * 1) to 409.
	 *
	 * @param exception the exception thrown by the service layer
	 * @param request the failed request, used to report {@link ApiError#path()}
	 * @return the 409 response body
	 */
	@ExceptionHandler(HighlightLimitExceededException.class)
	public ResponseEntity<ApiError> handleConflict(
			HighlightLimitExceededException exception, HttpServletRequest request) {
		return respond(HttpStatus.CONFLICT, exception.getMessage(), request);
	}

	/**
	 * Maps {@link CarAlreadySoldException} (selling a car that is already marked as sold) to 409.
	 *
	 * @param exception the exception thrown by the service layer
	 * @param request the failed request, used to report {@link ApiError#path()}
	 * @return the 409 response body
	 */
	@ExceptionHandler(CarAlreadySoldException.class)
	public ResponseEntity<ApiError> handleAlreadySold(
			CarAlreadySoldException exception, HttpServletRequest request) {
		return respond(HttpStatus.CONFLICT, exception.getMessage(), request);
	}

	/**
	 * Maps a {@code jakarta.validation} failure on an {@code @Valid @RequestBody} argument to 400,
	 * with a message that names every invalid field (TASK-008 requirement 3 / acceptance criterion
	 * 2: "o corpo identifica o campo {@code preco}").
	 *
	 * @param exception the validation failure Spring MVC raises for an invalid request body
	 * @param request the failed request, used to report {@link ApiError#path()}
	 * @return the 400 response body
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleInvalidBody(
			MethodArgumentNotValidException exception, HttpServletRequest request) {
		String message =
				exception.getBindingResult().getFieldErrors().stream()
						.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
						.collect(Collectors.joining("; "));
		return respond(HttpStatus.BAD_REQUEST, message, request);
	}

	/**
	 * Maps Spring Security's {@link AccessDeniedException} (raised by {@code @PreAuthorize} when an
	 * authenticated caller lacks the required role/authority) to 403, using the same {@link
	 * ApiError} shape as every other mapped exception instead of Spring Security's default,
	 * body-less 403.
	 *
	 * <p>ASSUNÇÃO: not requested by TASK-008 requirement 5 (which only lists 400/404/409), added
	 * because {@code backlog/reviews/TASK-007-r2.md} (nota 3 ao planner) asks this task to exercise
	 * {@code @EnableMethodSecurity} with a real 403 case, and returning it in the same envelope as
	 * every other error keeps the contract consistent for the frontend that will consume it
	 * (TASK-022).
	 *
	 * @param exception the exception {@code @PreAuthorize} raises when denying access
	 * @param request the failed request, used to report {@link ApiError#path()}
	 * @return the 403 response body
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(
			AccessDeniedException exception, HttpServletRequest request) {
		return respond(
				HttpStatus.FORBIDDEN, "Autenticado, mas sem a role exigida para esta operacao", request);
	}

	/**
	 * Maps a malformed or unreadable request body (invalid JSON, wrong content type body, ...) to
	 * 400, instead of Spring MVC's default body-less 400 (IMPORTANTE 4, {@code
	 * backlog/reviews/TASK-008-r1.md}).
	 *
	 * @param exception the exception Spring MVC raises when the {@code @RequestBody} cannot be
	 *     deserialised
	 * @param request the failed request, used to report {@link ApiError#path()}
	 * @return the 400 response body
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleMalformedBody(
			HttpMessageNotReadableException exception, HttpServletRequest request) {
		return respond(HttpStatus.BAD_REQUEST, "Corpo do pedido invalido ou malformado", request);
	}

	/**
	 * Maps a path variable or query parameter that cannot be converted to its expected type (e.g. a
	 * non-UUID {@code {id}} in the path) to 400, instead of Spring MVC's default body-less 400
	 * (IMPORTANTE 4, {@code backlog/reviews/TASK-008-r1.md}).
	 *
	 * @param exception the exception Spring MVC raises when argument conversion fails
	 * @param request the failed request, used to report {@link ApiError#path()}
	 * @return the 400 response body
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
		return respond(
				HttpStatus.BAD_REQUEST,
				"Parametro '" + exception.getName() + "' com valor invalido",
				request);
	}

	/**
	 * Maps a {@code ?sort=} query parameter that names a property {@link
	 * pt.diamondcars.dcbobackend.domain.car.Car} does not have to 400, instead of letting it reach
	 * Spring Data unmapped and surface as a 500 (IMPORTANTE 4, {@code
	 * backlog/reviews/TASK-008-r1.md}).
	 *
	 * @param exception the exception Spring Data raises when resolving an unknown sort property
	 * @param request the failed request, used to report {@link ApiError#path()}
	 * @return the 400 response body
	 */
	@ExceptionHandler(PropertyReferenceException.class)
	public ResponseEntity<ApiError> handleUnknownSortProperty(
			PropertyReferenceException exception, HttpServletRequest request) {
		return respond(
				HttpStatus.BAD_REQUEST,
				"Campo de ordenacao desconhecido: " + exception.getPropertyName(),
				request);
	}

	/**
	 * Maps a database constraint violation (e.g. a foreign key that no longer resolves at flush
	 * time) to 409, without propagating the underlying Postgres message — which names internal
	 * constraint/column identifiers that should not leak to a client (IMPORTANTE 4, {@code
	 * backlog/reviews/TASK-008-r1.md}). The full exception is still logged server-side for
	 * diagnosis.
	 *
	 * @param exception the exception the persistence layer raises on a constraint violation
	 * @param request the failed request, used to report {@link ApiError#path()}
	 * @return the 409 response body
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleDataIntegrityViolation(
			DataIntegrityViolationException exception, HttpServletRequest request) {
		log.warn("Data integrity violation handling {}", request.getRequestURI(), exception);
		return respond(
				HttpStatus.CONFLICT, "Pedido em conflito com o estado atual dos dados", request);
	}

	/**
	 * Fallback for every exception not mapped above, so the API never leaks a stack trace or an
	 * inconsistent envelope for an unanticipated failure (IMPORTANTE 4, {@code
	 * backlog/reviews/TASK-008-r1.md}). The full exception is still logged server-side for
	 * diagnosis; only a generic message reaches the client.
	 *
	 * @param exception the unmapped exception
	 * @param request the failed request, used to report {@link ApiError#path()}
	 * @return the 500 response body
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("Unexpected error handling {}", request.getRequestURI(), exception);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado", request);
	}

	private ResponseEntity<ApiError> respond(HttpStatus status, String message, HttpServletRequest request) {
		return ResponseEntity.status(status).body(ApiError.of(status, message, request.getRequestURI()));
	}
}
