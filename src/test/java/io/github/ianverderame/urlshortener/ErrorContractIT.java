package io.github.ianverderame.urlshortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Issue #5: every error class comes back as {@code application/problem+json} with type, title, and
 * status, including errors raised outside the controllers. The 503 case needs a scripted code
 * generator and lives in {@link LinkCreationCollisionIT}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ErrorContractIT {

  private static final String VALID_BODY = "{\"destination\":\"https://example.com/some/path\"}";

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void anInvalidDestinationIsAValidationProblem() {
    RestTestClient.ResponseSpec response =
        app.post()
            .uri("/api/v1/links")
            .header("X-API-Key", TestApiKey.VALUE)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"destination\":\"ftp://example.com/some/path\"}")
            .exchange();

    assertProblem(response, 400);
  }

  @Test
  void malformedJsonIsABadRequestProblem() {
    RestTestClient.ResponseSpec response =
        app.post()
            .uri("/api/v1/links")
            .header("X-API-Key", TestApiKey.VALUE)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"destination\":")
            .exchange();

    assertProblem(response, 400);
  }

  @Test
  void aMissingApiKeyIsAnUnauthorizedProblem() {
    RestTestClient.ResponseSpec response =
        app.post()
            .uri("/api/v1/links")
            .contentType(MediaType.APPLICATION_JSON)
            .body(VALID_BODY)
            .exchange();

    assertProblem(response, 401);
  }

  @Test
  void anUnknownCodeIsANotFoundProblem() {
    assertProblem(app.get().uri("/Zz9Zz9Zz").exchange(), 404);
  }

  @Test
  void anUnmatchedRouteIsANotFoundProblem() {
    assertProblem(app.get().uri("/no/such/route").exchange(), 404);
  }

  @Test
  void anUnsupportedMethodIsAMethodNotAllowedProblem() {
    RestTestClient.ResponseSpec response =
        app.get().uri("/api/v1/links").header("X-API-Key", TestApiKey.VALUE).exchange();

    assertProblem(response, 405);
  }

  @Test
  void anUnsupportedContentTypeIsAnUnsupportedMediaTypeProblem() {
    RestTestClient.ResponseSpec response =
        app.post()
            .uri("/api/v1/links")
            .header("X-API-Key", TestApiKey.VALUE)
            .contentType(MediaType.TEXT_PLAIN)
            .body("https://example.com/some/path")
            .exchange();

    assertProblem(response, 415);
  }

  static void assertProblem(RestTestClient.ResponseSpec response, int status) {
    response
        .expectStatus()
        .isEqualTo(status)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isNotEmpty()
        .jsonPath("$.title")
        .isNotEmpty()
        .jsonPath("$.status")
        .isEqualTo(status);
  }
}
