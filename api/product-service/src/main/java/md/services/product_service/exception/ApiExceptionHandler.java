package md.services.product_service.exception;

import java.net.URI;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
		String detail = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail);
	}

	@ExceptionHandler(ProductValidationException.class)
	ProblemDetail handleProductValidation(ProductValidationException exception) {
		return problem(HttpStatus.BAD_REQUEST, "Validation failed", exception.getMessage());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleResourceNotFound(ResourceNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
	}

	@ExceptionHandler({ DuplicateResourceException.class, DuplicateKeyException.class })
	ProblemDetail handleDuplicateResource(Exception exception) {
		return problem(HttpStatus.CONFLICT, "Resource already exists", exception.getMessage());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ProblemDetail handleNoResourceFound(NoResourceFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
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

}
