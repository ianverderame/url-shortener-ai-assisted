package io.github.ianverderame.urlshortener.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * One of the service's own errors, carrying the RFC 7807 problem it answers with: a status and a
 * fixed, safe detail, never anything from the request. {@link ProblemDetailsAdvice} answers it the
 * way it answers any of Spring's {@link ErrorResponseException}s, so a new error needs no handler,
 * and this package needs no knowledge of the features that throw them (#35).
 */
public abstract class ProblemException extends ErrorResponseException {

  private static final long serialVersionUID = 1L;

  protected ProblemException(HttpStatus status, String detail) {
    super(status, ProblemDetailsAdvice.problem(status, detail), null);
  }

  /** The detail, rather than Spring's status-and-body summary: a log shows what the client sees. */
  @Override
  public String getMessage() {
    return getBody().getDetail();
  }
}
