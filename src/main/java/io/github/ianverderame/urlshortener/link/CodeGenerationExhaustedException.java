package io.github.ianverderame.urlshortener.link;

import io.github.ianverderame.urlshortener.web.ProblemException;
import org.springframework.http.HttpStatus;

/** Every attempt to insert a link under a fresh short code hit an existing code. */
class CodeGenerationExhaustedException extends ProblemException {

  private static final long serialVersionUID = 1L;

  CodeGenerationExhaustedException() {
    super(HttpStatus.SERVICE_UNAVAILABLE, "Could not allocate a short code. Try again.");
  }
}
