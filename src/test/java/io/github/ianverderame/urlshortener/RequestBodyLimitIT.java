package io.github.ianverderame.urlshortener;

import static io.github.ianverderame.urlshortener.ErrorContractIT.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
 * Issue #26: create rejects a body over 8 KiB with a 413 problem and a body with an unknown JSON
 * property with a 400 problem, and a request without a valid key still gets 401 first. That the cap
 * stops reading instead of consuming the whole body is pinned in {@link RequestBodyLimitTest}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RequestBodyLimitIT {

  private static final int CAP = 8 * 1024;
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

  // Defect found on #9: an unknown property was ignored and the link created.
  @Test
  void anUnknownPropertyIsABadRequestProblem() {
    String body = "{\"destination\":\"" + DESTINATION + "\",\"padding\":\"x\"}";

    assertProblem(create(TestApiKey.VALUE, body), 400);
  }

  @Test
  void aBodyOverTheCapIsAPayloadTooLargeProblem() {
    assertProblem(create(TestApiKey.VALUE, validBodyOfSize(CAP + 1)), 413);
  }

  // A streamed body declares no length, so the cap has to count what arrives. Sent over a raw
  // socket
  // because the test client always sends a Content-Length.
  @Test
  void aChunkedBodyOverTheCapIsAPayloadTooLargeProblem() throws IOException {
    String body = validBodyOfSize(CAP + 1);
    String request =
        "POST /api/v1/links HTTP/1.1\r\n"
            + "Host: localhost\r\n"
            + "X-API-Key: "
            + TestApiKey.VALUE
            + "\r\n"
            + "Content-Type: application/json\r\n"
            + "Transfer-Encoding: chunked\r\n"
            + "Connection: close\r\n\r\n"
            + Integer.toHexString(body.length())
            + "\r\n"
            + body
            + "\r\n0\r\n\r\n";

    String response = rawRequest(request);
    int headerEnd = response.indexOf("\r\n\r\n");
    String head = response.substring(0, headerEnd);

    assertThat(head).startsWith("HTTP/1.1 413");
    assertThat(head.toLowerCase()).contains("content-type: application/problem+json");
  }

  @Test
  void aBodyOfExactlyTheCapIsProcessedNormally() {
    create(TestApiKey.VALUE, validBodyOfSize(CAP))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.destination")
        .isEqualTo(DESTINATION);
  }

  @Test
  void anOversizedBodyWithoutAKeyIsUnauthorized() {
    RestTestClient.ResponseSpec response =
        app.post()
            .uri("/api/v1/links")
            .contentType(MediaType.APPLICATION_JSON)
            .body(validBodyOfSize(CAP + 1))
            .exchange();

    assertProblem(response, 401);
  }

  @Test
  void anOversizedBodyWithAWrongKeyIsUnauthorized() {
    assertProblem(create("wrong-key", validBodyOfSize(CAP + 1)), 401);
  }

  private RestTestClient.ResponseSpec create(String key, String body) {
    return app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private String rawRequest(String request) throws IOException {
    try (Socket socket = new Socket("localhost", serverPort)) {
      socket.setSoTimeout(10_000);
      OutputStream out = socket.getOutputStream();
      out.write(request.getBytes(StandardCharsets.ISO_8859_1));
      out.flush();
      InputStream in = socket.getInputStream();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** A valid create body padded with JSON whitespace to exactly {@code size} UTF-8 bytes. */
  static String validBodyOfSize(int size) {
    String json = "{\"destination\":\"" + DESTINATION + "\"}";
    String body = json.substring(0, json.length() - 1) + " ".repeat(size - json.length()) + "}";
    assertThat(body.getBytes(StandardCharsets.UTF_8)).hasSize(size);
    return body;
  }
}
