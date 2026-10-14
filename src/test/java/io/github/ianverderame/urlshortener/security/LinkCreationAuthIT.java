package io.github.ianverderame.urlshortener.security;

import io.github.ianverderame.urlshortener.ErrorContractIT;
import io.github.ianverderame.urlshortener.TestApiKey;
import io.github.ianverderame.urlshortener.TestcontainersConfiguration;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Issues #4 and #5: a missing or wrong API key on create returns a 401 problem, whatever the length
 * of the wrong key and however the create path is spelled.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.public-base-url=http://localhost:8080")
class LinkCreationAuthIT {

  private static final String DESTINATION = "https://example.com/some/path";

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
  void creatingALinkWithoutAnApiKeyHeaderReturnsUnauthorized() {
    app.post()
        .uri("/api/v1/links")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + DESTINATION + "\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @ParameterizedTest
  @MethodSource("wrongKeys")
  void creatingALinkWithAWrongApiKeyHeaderReturnsUnauthorized(String wrongKey) {
    ErrorContractIT.assertProblem(
        app.post()
            .uri("/api/v1/links")
            .header("X-API-Key", wrongKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"destination\":\"" + DESTINATION + "\"}")
            .exchange(),
        401);
  }

  static Stream<String> wrongKeys() {
    String key = TestApiKey.VALUE;
    char last = key.charAt(key.length() - 1);
    return Stream.of(
        // same length as the real key, last character changed
        key.substring(0, key.length() - 1) + (last == 'a' ? 'b' : 'a'),
        // different lengths
        key + "-wrong",
        key.substring(0, key.length() - 1),
        "",
        "short");
  }

  // The raw request URI differs from the path Spring MVC routes on; the key check must not.
  @Test
  void anEncodedSpellingOfTheCreatePathStillRequiresTheKey() {
    ErrorContractIT.assertProblem(createWithoutKey("/api/v1/%6Cinks"), 401);
  }

  // Spring Security's firewall rejects matrix parameters outright, before any key check, and the
  // rejection still comes back as a problem.
  @ParameterizedTest
  @ValueSource(strings = {"/api;x=1/v1/links", "/api/v1/links;x=1"})
  void aMatrixParameterSpellingOfTheCreatePathIsRejected(String path) {
    ErrorContractIT.assertProblem(createWithoutKey(path), 400);
  }

  private RestTestClient.ResponseSpec createWithoutKey(String path) {
    return app.post()
        .uri(URI.create("http://localhost:" + serverPort + path))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + DESTINATION + "\"}")
        .exchange();
  }
}
