package io.github.ianverderame.urlshortener;

/** The requested stats range breaks a limit; the message is a fixed, safe string. */
class InvalidStatsRangeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  InvalidStatsRangeException(String message) {
    super(message);
  }
}
