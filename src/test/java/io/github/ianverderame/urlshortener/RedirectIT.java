package io.github.ianverderame.urlshortener;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tracer bullet for issue #4: redirecting an existing short link.
 *
 * <p>Covers a hit (302, no-store), an unknown code (404), and the route's exact-format constraint.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.public-base-url=http://localhost:8080")
class RedirectIT {

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  @Autowired JdbcClient jdbc;

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
        .header("X-API-Key", TestApiKey.VALUE)
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

  // Any other path never reaches the database. Rows stored directly under codes the API could never
  // issue prove it: if the route matched these paths, the lookup would find them and redirect.
  @ParameterizedTest
  @ValueSource(strings = {"abcd-123", "abc1234", "abcd_123", "abcd.123"})
  void aPathOutsideTheCodeFormatNeverReachesTheLookup(String storedCode) {
    jdbc.sql(
            "INSERT INTO links (code, destination, created_at) VALUES (:code, :destination, now())")
        .param("code", storedCode)
        .param("destination", "https://example.com/")
        .update();

    app.get().uri("/" + storedCode).exchange().expectStatus().isNotFound();
  }

  private String createLink(String destination) {
    AtomicReference<String> code = new AtomicReference<>();
    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", TestApiKey.VALUE)
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
