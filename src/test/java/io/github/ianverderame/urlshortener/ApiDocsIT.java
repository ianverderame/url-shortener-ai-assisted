package io.github.ianverderame.urlshortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Issue #4: the OpenAPI description and interactive docs are served on the application port. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiDocsIT {

  @LocalServerPort int serverPort;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void theOpenApiDescriptionListsCreateAndRedirect() {
    app.get()
        .uri("/v3/api-docs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.paths['/api/v1/links'].post")
        .exists()
        .jsonPath("$.paths['/{code}'].get")
        .exists();
  }

  @Test
  void theInteractiveDocsLoad() {
    app.get()
        .uri("/swagger-ui/index.html")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_HTML);
  }
}
