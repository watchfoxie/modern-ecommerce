package md.services.order_service.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			WebRequest request) {
		ProblemDetail problem = baseProblem(HttpStatus.BAD_REQUEST, "Request validation failed",
				"One or more request fields failed validation.", request);
		problem.setType(URI.create("https://modern-ecommerce.local/problems/request-validation"));
		problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.toList());
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception,
			WebRequest request) {
		ProblemDetail problem = baseProblem(HttpStatus.BAD_REQUEST, "Request validation failed",
				exception.getMessage(), request);
		problem.setType(URI.create("https://modern-ecommerce.local/problems/request-validation"));
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception, WebRequest request) {
		HttpStatus status = httpStatus(exception.getStatusCode());
		ProblemDetail problem = baseProblem(status, title(status), exception.getReason(), request);
		problem.setType(URI.create("urn:modern-ecommerce:problem:" + status.value()));
		return ResponseEntity.status(status).body(problem);
	}

	private ProblemDetail baseProblem(HttpStatus status, String title, String detail, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
		return problem;
	}

	private HttpStatus httpStatus(HttpStatusCode statusCode) {
		return HttpStatus.valueOf(statusCode.value());
	}

	private String title(HttpStatus status) {
		return switch (status) {
			case UNAUTHORIZED -> "Authentication failed";
			case NOT_FOUND -> "Resource not found";
			case CONFLICT -> "Resource conflict";
			case UNPROCESSABLE_ENTITY, UNPROCESSABLE_CONTENT -> "Request cannot be processed";
			default -> "Request failed";
		};
	}

}
