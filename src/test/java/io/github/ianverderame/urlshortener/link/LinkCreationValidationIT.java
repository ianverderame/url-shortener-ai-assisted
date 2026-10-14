package io.github.ianverderame.urlshortener.link;

import io.github.ianverderame.urlshortener.ErrorContractIT;
import io.github.ianverderame.urlshortener.TestApiKey;
import io.github.ianverderame.urlshortener.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tracer bullet for issue #4: a non-http(s) destination, a missing host, or no destination at all
 * returns 400. The full URL policy's case table is {@link DestinationPolicyTest}, its HTTP path is
 * {@link DestinationPolicyIT}, and the problem body shape is covered by {@link ErrorContractIT}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.public-base-url=http://localhost:8080")
class LinkCreationValidationIT {

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
  void creatingALinkWithANonHttpSchemeReturnsBadRequest() {
    String destination = "ftp://example.com/some/path";

    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", TestApiKey.VALUE)
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
        .header("X-API-Key", TestApiKey.VALUE)
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
        .header("X-API-Key", TestApiKey.VALUE)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("Destination is required.")
        .jsonPath("$.reason")
        .doesNotExist();
  }
}
