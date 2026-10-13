package io.github.ianverderame.urlshortener;

/** No link has the requested short code. */
class LinkNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  LinkNotFoundException() {
    super("No link has this code.");
  }
}
