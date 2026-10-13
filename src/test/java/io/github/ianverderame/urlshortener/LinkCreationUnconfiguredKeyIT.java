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
 * Issue #4: with no API key configured, create fails closed; an empty key header must not match the
 * empty configuration. (Refusing to start without a key is issue #5.)
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.api-key=")
class LinkCreationUnconfiguredKeyIT {

  @LocalServerPort int serverPort;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void anEmptyKeyHeaderIsRejectedWhenNoKeyIsConfigured() {
    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", "")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"https://example.com/some/path\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }
}
