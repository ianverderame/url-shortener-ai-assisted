package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Issue #24: with the database stopped, requests fail fast with a specific problem. The defect was
 * a generic {@code 500} at {@code instance: "/error"}. A stopped container's published port refuses
 * connections at once, so this case shows the wrong status; the 30 s wait is reproduced by {@link
 * DatabaseHangIT}.
 *
 * <p>This class owns its database container, so stopping it can't affect any other test's context.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(DatabaseOutageIT.OwnDatabaseConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class DatabaseOutageIT {

  /** The "Bounded failure" SLO from #9. */
  static final Duration FAILURE_BOUND = Duration.ofSeconds(3);

  /** Backstop only, so a regression fails the test instead of hanging it. */
  static final Duration CLIENT_READ_TIMEOUT = Duration.ofSeconds(10);

  static final String DESTINATION = "https://example.com/some/path";

  /** A log line at WARN or ERROR, in Boot's default console format. */
  private static final Pattern WARN_OR_ERROR = Pattern.compile("^\\S+\\s+(WARN|ERROR) ");

  /** A stack trace's frames, causes, or opening exception line. */
  private static final Pattern STACK_TRACE =
      Pattern.compile("^(\\s+at |Caused by: |[\\w.$]+(Exception|Error)(: |$))");

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  @Autowired PostgreSQLContainer postgres;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = boundedClient(serverPort);
    stopDatabase();
  }

  @Test
  void createFailsFastWithAServiceUnavailableProblem() {
    assertServiceUnavailableAt(exchangeWithinBound(createRequest(app)), "/api/v1/links");
  }

  @Test
  void redirectFailsFastWithAServiceUnavailableProblem() {
    assertServiceUnavailableAt(exchangeWithinBound(app.get().uri("/abcd1234")), "/abcd1234");
  }

  @Test
  void aFailedCreateLogsOneLineWithoutAStackTraceDestinationOrKey(CapturedOutput output) {
    assertLoggedOneLineWithoutStackTraceOrSecrets(
        loggedDuring(output, () -> createRequest(app).exchange()));
  }

  @Test
  void aFailedRedirectLogsOneLineWithoutAStackTrace(CapturedOutput output) {
    assertLoggedOneLineWithoutStackTraceOrSecrets(
        loggedDuring(output, () -> app.get().uri("/abcd1234").exchange()));
  }

  static RestTestClient boundedClient(int port) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setReadTimeout(CLIENT_READ_TIMEOUT);
    return RestTestClient.bindToServer(requestFactory).baseUrl("http://localhost:" + port).build();
  }

  static RestTestClient.RequestHeadersSpec<?> createRequest(RestTestClient app) {
    return app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", TestApiKey.VALUE)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + DESTINATION + "\"}");
  }

  /** What was logged while the action ran. */
  static String loggedDuring(CapturedOutput output, Runnable action) {
    int start = output.getAll().length();
    action.run();
    return output.getAll().substring(start);
  }

  /**
   * One failure, one {@code WARN} or {@code ERROR} line (decided on #9): no stack trace, and no
   * destination or key anywhere in the output.
   */
  static void assertLoggedOneLineWithoutStackTraceOrSecrets(String logged) {
    List<String> lines = logged.lines().toList();
    assertThat(lines).filteredOn(line -> WARN_OR_ERROR.matcher(line).find()).hasSize(1);
    assertThat(lines).noneMatch(line -> STACK_TRACE.matcher(line).find());
    assertThat(logged).doesNotContain(DESTINATION).doesNotContain(TestApiKey.VALUE);
  }

  /** Sends the request and asserts the response arrived within {@link #FAILURE_BOUND}. */
  static RestTestClient.ResponseSpec exchangeWithinBound(
      RestTestClient.RequestHeadersSpec<?> request) {
    return exchangeWithin(request, FAILURE_BOUND);
  }

  /**
   * Sends the request and asserts the response arrived within the bound. The backstop surfaces as
   * an exception whose type depends on whether it hit the headers or the body, so it is recognized
   * by the time taken.
   */
  static RestTestClient.ResponseSpec exchangeWithin(
      RestTestClient.RequestHeadersSpec<?> request, Duration bound) {
    long start = System.nanoTime();
    RestTestClient.ResponseSpec response;
    try {
      response = request.exchange();
    } catch (RuntimeException e) {
      if (Duration.ofNanos(System.nanoTime() - start).compareTo(CLIENT_READ_TIMEOUT) < 0) {
        throw e;
      }
      throw new AssertionError(
          "No response within the "
              + CLIENT_READ_TIMEOUT.toSeconds()
              + " s backstop; the bound is "
              + bound.toSeconds()
              + " s",
          e);
    }
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThanOrEqualTo(bound);
    return response;
  }

  static void assertServiceUnavailableAt(RestTestClient.ResponseSpec response, String path) {
    ErrorContractIT.assertProblem(response, 503);
    response.expectBody().jsonPath("$.instance").isEqualTo(path);
  }

  /** Stops the container but keeps it, as {@code docker compose stop db} does. */
  private void stopDatabase() {
    if (postgres.isRunning()) {
      postgres.getDockerClient().stopContainerCmd(postgres.getContainerId()).exec();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class OwnDatabaseConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
      return new PostgreSQLContainer(TestcontainersConfiguration.POSTGRES_IMAGE);
    }
  }
}
