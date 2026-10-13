package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
 * Issue #4: the OpenAPI description and interactive docs are served on the application port.
 *
 * <p>Issue #17: the description matches what each operation actually returns, error responses are
 * problem details, and the API key is a security scheme on create only.
 *
 * <p>Issue #26: create also documents the 413 for a body over the cap.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiDocsIT {

  private static final String CREATE = "$.paths['/api/v1/links'].post";
  private static final String REDIRECT = "$.paths['/{code}'].get";
  private static final String STATS = "$.paths['/api/v1/links/{code}/stats'].get";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String PROBLEM_DETAIL_REF = "#/components/schemas/ProblemDetail";

  // Missing paths read as null (or an empty list for wildcards) instead of throwing, so absence can
  // be asserted directly.
  private static final Configuration LENIENT =
      Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);

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

  // #8: the interactive docs list the stats endpoint.
  @Test
  void theOpenApiDescriptionListsStats() {
    Object stats = apiDocs().read(STATS);
    assertThat(stats).as("stats operation").isNotNull();
  }

  // The error controller is plumbing for the error contract, not part of the API.
  @Test
  void theOpenApiDescriptionDoesNotListTheErrorEndpoint() {
    app.get()
        .uri("/v3/api-docs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.paths['/error']")
        .doesNotExist();
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

  // #17 AC1, plus #26's 413: create documents exactly 201, 400, 401, 413, 415 and 503.
  @Test
  void createDocumentsCreatedAndItsErrorStatuses() {
    Map<String, Object> responses = apiDocs().read(CREATE + ".responses");
    assertThat(responses).containsOnlyKeys("201", "400", "401", "413", "415", "503");
  }

  // #17 AC1: the 201 carries the link, not a problem detail.
  @Test
  void createDocumentsTheLinkAsItsCreatedBody() {
    DocumentContext docs = apiDocs();

    Map<String, Object> content = docs.read(CREATE + ".responses['201'].content");
    assertThat(content).as("201 content").isNotEmpty().doesNotContainKey(PROBLEM_JSON);
    List<String> refs = docs.read(CREATE + ".responses['201'].content.*.schema['$ref']");
    assertThat(refs).isNotEmpty().containsOnly("#/components/schemas/LinkResponse");
  }

  // #17 AC1 + constraint: each create failure is a ProblemDetail as application/problem+json.
  @Test
  void createDocumentsItsErrorsAsProblemResponses() {
    DocumentContext docs = apiDocs();

    for (String status : List.of("400", "401", "413", "415", "503")) {
      assertProblemResponse(docs, CREATE, status);
    }
  }

  // #17 AC2: redirect documents exactly 302 and 404, and the 302 carries a Location header.
  @Test
  void redirectDocumentsFoundWithALocationHeader() {
    DocumentContext docs = apiDocs();

    Map<String, Object> responses = docs.read(REDIRECT + ".responses");
    assertThat(responses).containsOnlyKeys("302", "404");

    Map<String, Object> headers = docs.read(REDIRECT + ".responses['302'].headers");
    assertThat(headers).as("302 headers").containsKey("Location");
  }

  // #17 AC2 + constraint: an unknown code is a ProblemDetail served as application/problem+json.
  @Test
  void redirectDocumentsNotFoundAsAProblemResponse() {
    assertProblemResponse(apiDocs(), REDIRECT, "404");
  }

  // #8: stats documents exactly 200, 400, 401 and 404, and the 200 carries the stats.
  @Test
  void statsDocumentsItsStatsAndItsErrorStatuses() {
    DocumentContext docs = apiDocs();

    Map<String, Object> responses = docs.read(STATS + ".responses");
    assertThat(responses).containsOnlyKeys("200", "400", "401", "404");

    List<String> refs = docs.read(STATS + ".responses['200'].content.*.schema['$ref']");
    assertThat(refs).isNotEmpty().containsOnly("#/components/schemas/LinkStats");
  }

  // #8: each stats failure is a ProblemDetail as application/problem+json.
  @Test
  void statsDocumentsItsErrorsAsProblemResponses() {
    DocumentContext docs = apiDocs();

    for (String status : List.of("400", "401", "404")) {
      assertProblemResponse(docs, STATS, status);
    }
  }

  // #8: stats requires the API key, like create.
  @Test
  void statsRequiresTheApiKey() {
    DocumentContext docs = apiDocs();
    List<String> schemeNames = apiKeySchemeNames(docs);
    assertThat(schemeNames).hasSize(1);
    String scheme = schemeNames.get(0);

    assertThat(effectiveSecurity(docs, STATS))
        .as("stats security requirements")
        .isNotEmpty()
        .allSatisfy(requirement -> assertThat(requirement).containsKey(scheme));
  }

  // #17 constraint: the referenced schema is a real RFC 7807 problem detail.
  @Test
  void theProblemDetailSchemaDescribesTheRfc7807Members() {
    DocumentContext docs = apiDocs();

    Map<String, Object> properties = docs.read("$.components.schemas.ProblemDetail.properties");
    assertThat(properties)
        .as("ProblemDetail properties")
        .containsKeys("type", "title", "status", "detail", "instance");
  }

  // #17 AC3: neither create nor redirect documents a 200, since neither returns one. Scoped to
  // these two operations so a later endpoint that genuinely returns 200 does not break it.
  @Test
  void noOperationDocumentsOk() {
    DocumentContext docs = apiDocs();

    for (String operation : List.of(CREATE, REDIRECT)) {
      Object ok = docs.read(operation + ".responses['200']");
      assertThat(ok).as("200 response of %s", operation).isNull();
    }
  }

  // #17 AC1 + constraint: the API key is an apiKey scheme sent in the X-API-Key header.
  @Test
  void theApiKeyIsDocumentedAsAHeaderSecurityScheme() {
    assertThat(apiKeySchemeNames(apiDocs())).hasSize(1);
  }

  // #17 AC1: create requires the API key. Every alternative must include it; an empty alternative
  // ({}) would make the key optional.
  @Test
  void createRequiresTheApiKey() {
    DocumentContext docs = apiDocs();
    List<String> schemeNames = apiKeySchemeNames(docs);
    assertThat(schemeNames).hasSize(1);
    String scheme = schemeNames.get(0);

    List<Map<String, Object>> security = effectiveSecurity(docs, CREATE);
    assertThat(security)
        .as("create security requirements")
        .isNotEmpty()
        .allSatisfy(requirement -> assertThat(requirement).containsKey(scheme));
  }

  // #17 AC2: the redirect stays public. Neither its own security nor an inherited global one may
  // name any scheme.
  @Test
  void redirectHasNoSecurityRequirement() {
    DocumentContext docs = apiDocs();

    List<Map<String, Object>> security = effectiveSecurity(docs, REDIRECT);
    assertThat(security)
        .as("redirect security requirements")
        .allSatisfy(requirement -> assertThat(requirement).isEmpty());
  }

  private DocumentContext apiDocs() {
    byte[] body =
        app.get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    assertThat(body).as("/v3/api-docs body").isNotNull();
    return JsonPath.using(LENIENT).parse(new String(body, StandardCharsets.UTF_8));
  }

  private static void assertProblemResponse(DocumentContext docs, String operation, String status) {
    String response = operation + ".responses['" + status + "']";

    Map<String, Object> content = docs.read(response + ".content");
    assertThat(content).as("%s content of %s", status, operation).containsKey(PROBLEM_JSON);

    String ref = docs.read(response + ".content['" + PROBLEM_JSON + "'].schema['$ref']");
    assertThat(ref).as("%s problem schema of %s", status, operation).isEqualTo(PROBLEM_DETAIL_REF);
  }

  private static List<String> apiKeySchemeNames(DocumentContext docs) {
    Map<String, Map<String, Object>> schemes = docs.read("$.components.securitySchemes");
    if (schemes == null) {
      return List.of();
    }
    return schemes.entrySet().stream()
        .filter(entry -> "apiKey".equals(entry.getValue().get("type")))
        .filter(entry -> "header".equals(entry.getValue().get("in")))
        .filter(entry -> "X-API-Key".equals(entry.getValue().get("name")))
        .map(Map.Entry::getKey)
        .toList();
  }

  // An operation's own security list replaces the global one; without its own, the global applies.
  private static List<Map<String, Object>> effectiveSecurity(
      DocumentContext docs, String operation) {
    List<Map<String, Object>> own = docs.read(operation + ".security");
    if (own != null) {
      return own;
    }
    List<Map<String, Object>> global = docs.read("$.security");
    return global == null ? List.of() : global;
  }
}
