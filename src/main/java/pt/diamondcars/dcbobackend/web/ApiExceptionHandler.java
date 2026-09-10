package pt.diamondcars.dcbobackend.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pt.diamondcars.dcbobackend.web.dto.ApiError;
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

	private ResponseEntity<ApiError> respond(HttpStatus status, String message, HttpServletRequest request) {
		return ResponseEntity.status(status).body(ApiError.of(status, message, request.getRequestURI()));
	}
}
