package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * The error contract's mapping, called directly: each of the service's own exceptions becomes a
 * problem with its status, a type, and its fixed detail, and framework problems get a type filled
 * in. That the advice is wired and serialized as RFC 7807 JSON is covered by the integration tests.
 */
class ProblemDetailsAdviceTest {

  private static final URI ABOUT_BLANK = URI.create("about:blank");

  private final ProblemDetailsAdvice advice = new ProblemDetailsAdvice();

  @Test
  void aPolicyRejectionIsABadRequestCarryingItsReason() {
    ProblemDetail problem =
        advice.invalidDestination(
            new InvalidDestinationException(RejectionReason.UNSUPPORTED_SCHEME));

    assertProblem(problem, HttpStatus.BAD_REQUEST, RejectionReason.UNSUPPORTED_SCHEME.detail());
    assertThat(problem.getProperties()).containsEntry("reason", "UNSUPPORTED_SCHEME");
  }

  @Test
  void aRequestShapeRejectionIsABadRequestWithoutAReason() {
    ProblemDetail problem =
        advice.invalidDestination(new InvalidDestinationException("A destination is required."));

    assertProblem(problem, HttpStatus.BAD_REQUEST, "A destination is required.");
    assertThat(problem.getProperties()).isNullOrEmpty();
  }

  @Test
  void anUnknownLinkIsNotFound() {
    assertProblem(
        advice.linkNotFound(new LinkNotFoundException()),
        HttpStatus.NOT_FOUND,
        "No link has this code.");
  }

  @Test
  void anInvalidStatsRangeIsABadRequestWithItsMessage() {
    assertProblem(
        advice.invalidStatsRange(new InvalidStatsRangeException("'from' must not be after 'to'.")),
        HttpStatus.BAD_REQUEST,
        "'from' must not be after 'to'.");
  }

  @Test
  void exhaustedCodeGenerationIsServiceUnavailable() {
    assertProblem(
        advice.codeGenerationExhausted(new CodeGenerationExhaustedException()),
        HttpStatus.SERVICE_UNAVAILABLE,
        "Could not allocate a short code. Try again.");
  }

  @Test
  void anOversizedBodyIsPayloadTooLargeNamingTheCapButNotTheBody() {
    assertProblem(
        advice.requestBodyTooLarge(new RequestBodyTooLargeException(8192)),
        HttpStatus.CONTENT_TOO_LARGE,
        "The request body must not exceed 8192 bytes.");
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
