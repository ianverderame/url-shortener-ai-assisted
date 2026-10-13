package io.github.ianverderame.urlshortener;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tracer bullet for issue #4: redirecting an existing short link.
 *
 * <p>Covers a hit (302, no-store), an unknown code (404), and the route's exact-format constraint.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
    properties = {
      "app.api-key=test-api-key-for-redirect-it",
      "app.public-base-url=http://localhost:8080"
    })
class RedirectIT {

  private static final String API_KEY = "test-api-key-for-redirect-it";

  @LocalServerPort int serverPort;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void gettingAnExistingCodeRedirectsToTheStoredDestinationWithoutCaching() {
    String destination = "https://example.com/some/path";
    AtomicReference<String> capturedCode = new AtomicReference<>();

    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", API_KEY)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + destination + "\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.code")
        .value((String code) -> capturedCode.set(code));

    app.get()
        .uri("/" + capturedCode.get())
        .exchange()
        .expectStatus()
        .isFound()
        .expectHeader()
        .valueEquals(HttpHeaders.LOCATION, destination)
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  @Test
  void gettingAnUnknownCodeReturnsNotFound() {
    app.get().uri("/Zz9Zz9Zz").exchange().expectStatus().isNotFound();
  }

  // The route only matches exactly 8 base62 characters; a prefix or extension of a real code must
  // not redirect.
  @ParameterizedTest
  @ValueSource(ints = {-1, 1})
  void aPathThatIsNotExactlyAnExistingCodeDoesNotRedirect(int lengthChange) {
    String code = createLink("https://example.com/some/path");
    String path = lengthChange < 0 ? code.substring(0, 7) : code + "0";

    app.get().uri("/" + path).exchange().expectStatus().isNotFound();
  }

  @Test
  void aPathWithNonBase62CharactersIsNotFound() {
    app.get().uri("/abcd-123").exchange().expectStatus().isNotFound();
  }

  private String createLink(String destination) {
    AtomicReference<String> code = new AtomicReference<>();
    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", API_KEY)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + destination + "\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.code")
        .value((String c) -> code.set(c));
    return code.get();
  }
}
