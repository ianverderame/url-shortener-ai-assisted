package io.github.ianverderame.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ianverderame.urlshortener.ProblemExceptionAssertions;
import java.net.URI;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.TypeMismatchException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * The error contract's mapping, called directly: a {@link ProblemException} is answered with its
 * own problem, infrastructure and framework failures get their status, a type, and a fixed detail,
 * and framework problems get a type filled in. Each feature's own problems are tested in its
 * package. That the advice is wired and serialized as RFC 7807 JSON is covered by the integration
 * tests.
 */
class ProblemDetailsAdviceTest {

  private static final URI ABOUT_BLANK = URI.create("about:blank");

  private final ProblemDetailsAdvice advice = new ProblemDetailsAdvice();

  @Test
  void aProblemExceptionIsAnsweredWithItsOwnStatusAndProblem() throws Exception {
    RequestBodyTooLargeException e = new RequestBodyTooLargeException(8192);

    ResponseEntity<Object> response =
        advice.handleException(e, new ServletWebRequest(new MockHttpServletRequest()));

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    assertThat(response.getBody()).isSameAs(e.getBody());
  }

  @Test
  void anOversizedBodyIsContentTooLargeNamingTheCapButNotTheBody() {
    ProblemExceptionAssertions.assertProblem(
        new RequestBodyTooLargeException(8192),
        HttpStatus.CONTENT_TOO_LARGE,
        "The request body must not exceed 8192 bytes.");
  }

  @Test
  void anUnavailableDatabaseIsServiceUnavailableWithoutTheCause() {
    assertProblem(
        advice.databaseUnavailable(
            new CannotGetJdbcConnectionException("db.internal:5432 refused")),
        HttpStatus.SERVICE_UNAVAILABLE,
        "The service is temporarily unavailable. Try again.");
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void anUnavailableDatabaseLogsOneLineNamingTheExceptionTypeButNotItsMessage(
      CapturedOutput output) {
    advice.databaseUnavailable(new CannotGetJdbcConnectionException("db.internal:5432 refused"));

    assertThat(output.getAll().lines())
        .singleElement()
        .asString()
        .contains("WARN")
        .contains("Database unavailable")
        .contains("CannotGetJdbcConnectionException")
        .doesNotContain("db.internal");
  }

  @Test
  void anAuthenticationFailureIsUnauthorizedAndNamesTheHeaderButNotTheCause() {
    ProblemDetail problem =
        advice.unauthenticated(new BadCredentialsException("key did not match"));

    assertProblem(problem, HttpStatus.UNAUTHORIZED, "A valid X-API-Key header is required.");
  }

  @Test
  void aMalformedParameterIsABadRequestNamingTheParameterButNotItsValue() {
    TypeMismatchException mismatch =
        new TypeMismatchException("https://example.com/secret", LocalDate.class);
    mismatch.initPropertyName("from");

    ResponseEntity<Object> response =
        advice.handleTypeMismatch(
            mismatch,
            new HttpHeaders(),
            HttpStatus.BAD_REQUEST,
            new ServletWebRequest(new MockHttpServletRequest()));

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
    assertProblem(
        (ProblemDetail) response.getBody(),
        HttpStatus.BAD_REQUEST,
        "Parameter 'from' is not valid.");
  }

  @Test
  void aFrameworkProblemWithoutATypeGetsTheDefaultType() {
    ProblemDetail body = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(body.getType()).as("precondition: no type yet").isNull();

    ResponseEntity<Object> response = frameworkResponse(body, HttpStatus.METHOD_NOT_ALLOWED);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(response.getBody()).isSameAs(body);
    assertThat(body.getType()).isEqualTo(ABOUT_BLANK);
  }

  @Test
  void aFrameworkProblemThatAlreadyHasATypeKeepsIt() {
    URI own = URI.create("https://example.com/problems/own");
    ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    body.setType(own);

    frameworkResponse(body, HttpStatus.BAD_REQUEST);

    assertThat(body.getType()).isEqualTo(own);
  }

  @Test
  void aBodyThatIsNotAProblemIsPassedThrough() {
    ResponseEntity<Object> response = frameworkResponse("plain", HttpStatus.BAD_REQUEST);

    assertThat(response.getBody()).isEqualTo("plain");
  }

  private ResponseEntity<Object> frameworkResponse(Object body, HttpStatus status) {
    return advice.createResponseEntity(
        body, new HttpHeaders(), status, new ServletWebRequest(new MockHttpServletRequest()));
  }

  private static void assertProblem(ProblemDetail problem, HttpStatus status, String detail) {
    assertThat(problem.getStatus()).isEqualTo(status.value());
    assertThat(problem.getType()).isEqualTo(ABOUT_BLANK);
    assertThat(problem.getDetail()).isEqualTo(detail);
  }
}
