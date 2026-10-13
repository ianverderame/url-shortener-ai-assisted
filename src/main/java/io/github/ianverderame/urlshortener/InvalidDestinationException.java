package io.github.ianverderame.urlshortener;

/** A destination was rejected. The message never contains the submitted URL. */
class InvalidDestinationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  InvalidDestinationException(String reason) {
    super(reason);
  }
}
