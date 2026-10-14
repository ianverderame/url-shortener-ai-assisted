package io.github.ianverderame.urlshortener.analytics;

import static io.github.ianverderame.urlshortener.ProblemExceptionAssertions.assertProblem;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * The problems this package answers with: each exception carries its own status and fixed detail
 * (#35). That they reach the client as RFC 7807 JSON is covered by the integration tests.
 */
class AnalyticsProblemsTest {

  @Test
  void anInvalidStatsRangeIsABadRequestWithItsFixedMessage() {
    assertProblem(
        new InvalidStatsRangeException("'from' must not be after 'to'."),
        HttpStatus.BAD_REQUEST,
        "'from' must not be after 'to'.");
  }
}
