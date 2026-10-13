package io.github.ianverderame.urlshortener;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The one error contract: every failure is an RFC 7807 problem with type, title, and status. The
 * framework's own exceptions (malformed JSON, 404 route, 405, 415, ...) come from the superclass;
 * the service's own are below. Details are fixed, safe strings: never a submitted URL or a key.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final URI DEFAULT_TYPE = URI.create("about:blank");

  @ExceptionHandler
  ProblemDetail invalidDestination(InvalidDestinationException e) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler
  ProblemDetail codeGenerationExhausted(CodeGenerationExhaustedException e) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unauthenticated(AuthenticationException e) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "A valid " + ApiKeyAuthenticationFilter.HEADER + " header is required.");
  }

  /** Framework problems: fill in the type, which is otherwise left out of the JSON. */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem && problem.getType() == null) {
      problem.setType(DEFAULT_TYPE);
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  static ProblemDetail problem(HttpStatusCode status, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(DEFAULT_TYPE);
    return problem;
  }
}
