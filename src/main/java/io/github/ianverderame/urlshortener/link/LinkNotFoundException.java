package io.github.ianverderame.urlshortener.link;

import io.github.ianverderame.urlshortener.web.ProblemException;
import org.springframework.http.HttpStatus;

/** No link has the requested short code. */
public class LinkNotFoundException extends ProblemException {

  private static final long serialVersionUID = 1L;

  public LinkNotFoundException() {
    super(HttpStatus.NOT_FOUND, "No link has this code.");
  }
}
