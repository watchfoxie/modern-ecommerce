package md.services.user_service.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

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

	private ProblemDetail baseProblem(HttpStatus status, String title, String detail, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
		return problem;
	}

}
