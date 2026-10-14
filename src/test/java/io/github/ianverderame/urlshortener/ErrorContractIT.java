package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Issue #5: every error class comes back as {@code application/problem+json} with type, title, and
 * status, including errors raised outside the controllers. The 503 case needs a scripted code
 * generator and lives in {@code link.LinkCreationCollisionIT}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public class ErrorContractIT {

  private static final String VALID_BODY = "{\"destination\":\"https://example.com/some/path\"}";

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  @LocalManagementPort int managementPort;

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

  // Tomcat rejects these before any servlet runs, so Spring's error handling never sees them. Sent
  // over a raw socket because HTTP clients encode or refuse such paths. Both servers are checked:
  // the management port is a separate Tomcat with its own error report.
  @ParameterizedTest
  @CsvSource({
    "app, /a|b",
    "app, /a%2Fb",
    "app, /a{b",
    "management, /a|b",
    "management, /a%2Fb",
    "management, /a{b"
  })
  void aRequestTomcatRejectsIsABadRequestProblem(String server, String rawPath) throws IOException {
    int port = server.equals("management") ? managementPort : serverPort;
    String response = rawRequest(port, "GET " + rawPath + " HTTP/1.0\r\n\r\n");
    int headerEnd = response.indexOf("\r\n\r\n");
    String head = response.substring(0, headerEnd);

    assertThat(head).startsWith("HTTP/1.1 400");
    assertThat(head.toLowerCase()).contains("content-type: application/problem+json");
    JsonNode body = JsonMapper.builder().build().readTree(response.substring(headerEnd + 4));
    assertThat(body.path("type").asString()).isEqualTo("about:blank");
    assertThat(body.path("title").asString()).isEqualTo("Bad Request");
    assertThat(body.path("status").asInt()).isEqualTo(400);
  }

  private static String rawRequest(int port, String request) throws IOException {
    try (Socket socket = new Socket("localhost", port)) {
      socket.setSoTimeout(10_000);
      OutputStream out = socket.getOutputStream();
      out.write(request.getBytes(StandardCharsets.ISO_8859_1));
      out.flush();
      InputStream in = socket.getInputStream();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  public static void assertProblem(RestTestClient.ResponseSpec response, int status) {
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
