package io.github.ianverderame.urlshortener.link;

import static io.github.ianverderame.urlshortener.ProblemExceptionAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * The problems this package answers with: each exception carries its own status and fixed detail
 * (#35). That they reach the client as RFC 7807 JSON is covered by the integration tests.
 */
class LinkProblemsTest {

  @Test
  void aPolicyRejectionIsABadRequestCarryingItsReason() {
    InvalidDestinationException e =
        new InvalidDestinationException(RejectionReason.UNSUPPORTED_SCHEME);

    assertProblem(e, HttpStatus.BAD_REQUEST, RejectionReason.UNSUPPORTED_SCHEME.detail());
    assertThat(e.getBody().getProperties()).containsExactly(entry("reason", "UNSUPPORTED_SCHEME"));
  }

  @Test
  void aRequestShapeRejectionIsABadRequestWithoutAReason() {
    InvalidDestinationException e = new InvalidDestinationException("Destination is required.");

    assertProblem(e, HttpStatus.BAD_REQUEST, "Destination is required.");
    assertThat(e.getBody().getProperties()).isNullOrEmpty();
  }

  @Test
  void anUnknownLinkIsNotFound() {
    assertProblem(new LinkNotFoundException(), HttpStatus.NOT_FOUND, "No link has this code.");
  }

  @Test
  void exhaustedCodeGenerationIsServiceUnavailable() {
    assertProblem(
        new CodeGenerationExhaustedException(),
        HttpStatus.SERVICE_UNAVAILABLE,
        "Could not allocate a short code. Try again.");
  }
}
