package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ianverderame.urlshortener.web.ProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;

/** Checks the problem one of the service's own exceptions answers with, without a request. */
public final class ProblemExceptionAssertions {

  private ProblemExceptionAssertions() {}

  /**
   * The status, a type, and the fixed detail; the message is that same detail, so a log line shows
   * only what the client sees.
   */
  public static void assertProblem(ProblemException e, HttpStatus status, String detail) {
    assertThat(e.getStatusCode()).isEqualTo(status);
    assertThat(e.getBody().getStatus()).isEqualTo(status.value());
    assertThat(e.getBody().getType()).isEqualTo(URI.create("about:blank"));
    assertThat(e.getBody().getDetail()).isEqualTo(detail);
    assertThat(e.getMessage()).isEqualTo(detail);
  }
}
