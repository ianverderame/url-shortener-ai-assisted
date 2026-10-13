package io.github.ianverderame.urlshortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tracer bullet for issue #4: a non-http(s) destination on create returns 400.
 *
 * <p>Grows one vertical slice at a time; this slice covers only the minimal validation called out
 * in the issue's constraints: scheme must be http or https, and a host must be present. The full
 * adversarial URL policy (userinfo, control characters, length limits, self-host, non-ASCII hosts,
 * etc.) is issue #6 and is explicitly out of scope here. Error body shape (RFC 7807) is also out of
 * scope; this slice asserts status only.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
    properties = {
      "app.api-key=test-api-key-for-link-creation-validation-it",
      "app.public-base-url=http://localhost:8080"
    })
class LinkCreationValidationIT {

  private static final String API_KEY = "test-api-key-for-link-creation-validation-it";

  @LocalServerPort int serverPort;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void creatingALinkWithANonHttpSchemeReturnsBadRequest() {
    String destination = "ftp://example.com/some/path";

    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", API_KEY)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + destination + "\"}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void creatingALinkWithoutAHostReturnsBadRequest() {
    String destination = "https:///some/path";

    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", API_KEY)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + destination + "\"}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void creatingALinkWithoutADestinationReturnsBadRequest() {
    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", API_KEY)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }
}
