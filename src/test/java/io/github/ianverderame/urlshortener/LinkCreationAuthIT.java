package io.github.ianverderame.urlshortener;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tracer bullet for issue #4: a missing or wrong API key on create returns 401.
 *
 * <p>Grows one vertical slice at a time; this slice covers only the key-check on create. Error body
 * shape (RFC 7807) is out of scope here; that's issue #5.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
    properties = {
      "app.api-key=test-api-key-for-link-creation-auth-it",
      "app.public-base-url=http://localhost:8080"
    })
class LinkCreationAuthIT {

  private static final String API_KEY = "test-api-key-for-link-creation-auth-it";
  private static final String DESTINATION = "https://example.com/some/path";

  @LocalServerPort int serverPort;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void creatingALinkWithoutAnApiKeyHeaderReturnsUnauthorized() {
    app.post()
        .uri("/api/v1/links")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + DESTINATION + "\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void creatingALinkWithAWrongApiKeyHeaderReturnsUnauthorized() {
    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", API_KEY + "-wrong")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + DESTINATION + "\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  // The raw request URI differs from the path Spring MVC routes on; the key check must not.
  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/%6Cinks", "/api;x=1/v1/links", "/api/v1/links;x=1"})
  void anEquivalentSpellingOfTheCreatePathStillRequiresTheKey(String path) {
    app.post()
        .uri(URI.create("http://localhost:" + serverPort + path))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + DESTINATION + "\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }
}
