package io.github.ianverderame.urlshortener.link;

/** A destination was rejected. The message never contains the submitted URL. */
public class InvalidDestinationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final RejectionReason reason;

  public InvalidDestinationException(RejectionReason reason) {
    super(reason.detail());
    this.reason = reason;
  }

  /** A request-shape failure (no destination at all), checked before the policy runs. */
  public InvalidDestinationException(String detail) {
    super(detail);
    this.reason = null;
  }

  /** The policy's reason, or null when the request never reached the policy. */
  public RejectionReason reason() {
    return reason;
  }
}
