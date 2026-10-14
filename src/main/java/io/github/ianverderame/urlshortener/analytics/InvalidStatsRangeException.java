package io.github.ianverderame.urlshortener.analytics;

/** The requested stats range breaks a limit; the message is a fixed, safe string. */
public class InvalidStatsRangeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidStatsRangeException(String message) {
    super(message);
  }
}
