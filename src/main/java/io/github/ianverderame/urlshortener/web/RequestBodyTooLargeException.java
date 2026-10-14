package io.github.ianverderame.urlshortener.web;

import org.springframework.http.HttpStatus;

/** A request body over the cap: a 413 whose detail names the cap, never anything from the body. */
class RequestBodyTooLargeException extends ProblemException {

  private static final long serialVersionUID = 1L;

  RequestBodyTooLargeException(int maxBytes) {
    super(HttpStatus.CONTENT_TOO_LARGE, "The request body must not exceed " + maxBytes + " bytes.");
  }
}
