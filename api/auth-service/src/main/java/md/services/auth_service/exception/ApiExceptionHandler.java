package md.services.auth_service.exception;

import java.net.URI;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
		String detail = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Request cannot be processed", exception.getMessage());
	}

	@ExceptionHandler(UnauthorizedException.class)
	ProblemDetail handleUnauthorized(UnauthorizedException exception) {
		return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", exception.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException exception) {
		return problem(HttpStatus.CONFLICT, "Resource conflict", exception.getMessage());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ProblemDetail handleNoResourceFound(NoResourceFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
	}

	@ExceptionHandler(ResponseStatusException.class)
	ProblemDetail handleResponseStatus(ResponseStatusException exception) {
		HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
		return problem(status, title(status), exception.getReason());
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception exception) {
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", "The request failed unexpectedly.");
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("urn:modern-ecommerce:problem:" + status.value()));
		return problem;
	}

	private String title(HttpStatus status) {
		return switch (status) {
			case UNAUTHORIZED -> "Authentication failed";
			case FORBIDDEN -> "Access denied";
			case NOT_FOUND -> "Resource not found";
			case CONFLICT -> "Resource conflict";
			case UNPROCESSABLE_ENTITY, UNPROCESSABLE_CONTENT -> "Request cannot be processed";
			case SERVICE_UNAVAILABLE -> "Service unavailable";
			default -> status.getReasonPhrase();
		};
	}

}
