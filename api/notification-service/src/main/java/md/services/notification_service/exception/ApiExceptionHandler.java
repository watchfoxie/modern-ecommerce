package md.services.notification_service.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NoResourceFoundException.class)
	ProblemDetail handleNoResourceFound(NoResourceFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception exception) {
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected notification-service error",
				"Unexpected error while processing notification diagnostic request.");
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("urn:modern-ecommerce:problem:" + status.value()));
		return problem;
	}
}
