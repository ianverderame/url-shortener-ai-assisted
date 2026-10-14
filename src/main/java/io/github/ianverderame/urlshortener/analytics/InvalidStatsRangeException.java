package io.github.ianverderame.urlshortener.analytics;

import io.github.ianverderame.urlshortener.web.ProblemException;
import org.springframework.http.HttpStatus;

/** The requested stats range breaks a limit: a 400 whose detail is a fixed, safe string. */
class InvalidStatsRangeException extends ProblemException {

  private static final long serialVersionUID = 1L;

  InvalidStatsRangeException(String message) {
    super(HttpStatus.BAD_REQUEST, message);
  }
}
