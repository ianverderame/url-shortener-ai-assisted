package io.github.ianverderame.urlshortener.link;

import io.github.ianverderame.urlshortener.web.ProblemException;
import org.springframework.http.HttpStatus;

/**
 * A destination was rejected: a 400 whose detail never contains the submitted URL. A policy
 * rejection also carries its reason code as a top-level {@code reason} property.
 */
class InvalidDestinationException extends ProblemException {

  private static final long serialVersionUID = 1L;

  private final RejectionReason reason;

  InvalidDestinationException(RejectionReason reason) {
    super(HttpStatus.BAD_REQUEST, reason.detail());
    this.reason = reason;
    getBody().setProperty("reason", reason.name());
  }

  /** A request-shape failure (no destination at all), checked before the policy runs. */
  InvalidDestinationException(String detail) {
    super(HttpStatus.BAD_REQUEST, detail);
    this.reason = null;
  }

  /** The policy's reason, or null when the request never reached the policy. */
  RejectionReason reason() {
    return reason;
  }
}
