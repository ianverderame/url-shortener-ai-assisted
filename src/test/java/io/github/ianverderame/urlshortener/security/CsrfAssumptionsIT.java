package io.github.ianverderame.urlshortener.security;

import io.github.ianverderame.urlshortener.TestApiKey;
import io.github.ianverderame.urlshortener.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Issue #5: CSRF protection is disabled (see {@link SecurityConfig}) because nothing a browser
 * attaches on its own authenticates a request: the only credential is the {@code X-API-Key} header,
 * there are no cookies or sessions, and no cross-origin caller is allowed. These tests pin those
 * assumptions; if one fails, revisit the CSRF decision before changing the test.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CsrfAssumptionsIT {

  private static final String OTHER_ORIGIN = "https://other-site.example";

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
  void anAuthenticatedCreateSetsNoCookie() {
    create(TestApiKey.VALUE)
        .expectStatus()
        .isCreated()
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE);
  }

  @Test
  void aRejectedCreateSetsNoCookie() {
    create("not-the-key")
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE);
  }

  @Test
  void aCrossOriginPreflightForCreateIsNotAllowed() {
    app.method(HttpMethod.OPTIONS)
        .uri("/api/v1/links")
        .header(HttpHeaders.ORIGIN, OTHER_ORIGIN)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-API-Key, Content-Type")
        .exchange()
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
  }

  @Test
  void aCrossOriginCreateIsNotGrantedToTheOrigin() {
    app.post()
        .uri("/api/v1/links")
        .header(HttpHeaders.ORIGIN, OTHER_ORIGIN)
        .header("X-API-Key", TestApiKey.VALUE)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"https://example.com/some/path\"}")
        .exchange()
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE);
  }

  private RestTestClient.ResponseSpec create(String key) {
    return app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"https://example.com/some/path\"}")
        .exchange();
  }
}
