package io.github.ianverderame.urlshortener.web;

import io.github.ianverderame.urlshortener.analytics.InvalidStatsRangeException;
import io.github.ianverderame.urlshortener.link.CodeGenerationExhaustedException;
import io.github.ianverderame.urlshortener.link.InvalidDestinationException;
import io.github.ianverderame.urlshortener.link.LinkNotFoundException;
import io.github.ianverderame.urlshortener.security.ApiKeyAuthenticationFilter;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The one error contract: every failure is an RFC 7807 problem with type, title, and status. The
 * framework's own exceptions (malformed JSON, 404 route, 405, 415, ...) come from the superclass;
 * the service's own are below. Details are fixed, safe strings: never a submitted URL or a key. The
 * one framework detail that would echo input (a malformed parameter) is overridden below.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

  private static final URI DEFAULT_TYPE = URI.create("about:blank");

  /** Policy rejections also carry the reason code as a top-level {@code reason} property. */
  @ExceptionHandler
  ProblemDetail invalidDestination(InvalidDestinationException e) {
    ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, e.getMessage());
    if (e.reason() != null) {
      problem.setProperty("reason", e.reason().name());
    }
    return problem;
  }

  @ExceptionHandler
  ProblemDetail linkNotFound(LinkNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler
  ProblemDetail invalidStatsRange(InvalidStatsRangeException e) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler
  ProblemDetail codeGenerationExhausted(CodeGenerationExhaustedException e) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
  }

  /**
   * The database is down or hung (decided on #9): a 503 within the pool and socket timeouts. A
   * failed commit or rollback is one too: when a query times out, the rollback on the dead
   * connection fails and replaces the original exception. (No constraint is deferred to commit.)
   * The detail is fixed, and the one log line names only the exception type, because the cause's
   * message can name the database host. Library loggers that would add lines or a stack trace for
   * the same failure are quieted in application.properties.
   */
  @ExceptionHandler({
    DataAccessResourceFailureException.class,
    CannotCreateTransactionException.class,
    TransactionSystemException.class
  })
  ProblemDetail databaseUnavailable(Exception e) {
    log.warn("Database unavailable: answered 503 ({})", e.getClass().getSimpleName());
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable. Try again.");
  }

  @ExceptionHandler
  ProblemDetail requestBodyTooLarge(RequestBodyTooLargeException e) {
    return problem(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unauthenticated(AuthenticationException e) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "A valid " + ApiKeyAuthenticationFilter.HEADER + " header is required.");
  }

  /** A malformed parameter names the parameter, never its value, which could carry a URL. */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail body =
        problem(HttpStatus.BAD_REQUEST, "Parameter '" + ex.getPropertyName() + "' is not valid.");
    return handleExceptionInternal(ex, body, headers, status, request);
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
