package io.github.ianverderame.urlshortener.web;

/** A request body over the cap. The message names the cap, never anything from the body. */
class RequestBodyTooLargeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  RequestBodyTooLargeException(int maxBytes) {
    super("The request body must not exceed " + maxBytes + " bytes.");
  }
}
