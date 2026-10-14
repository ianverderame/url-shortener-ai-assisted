package io.github.ianverderame.urlshortener.link;

/** No link has the requested short code. */
public class LinkNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LinkNotFoundException() {
    super("No link has this code.");
  }
}
