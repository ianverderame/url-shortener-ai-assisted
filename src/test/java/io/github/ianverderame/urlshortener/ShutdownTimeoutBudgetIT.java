package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Issue #27: the management context's shutdown-phase timeout must be set explicitly, inside the
 * budget decided on #9. The defect was that it isn't set at all, so it falls back to Spring
 * Framework's own {@code DefaultLifecycleProcessor} default (10 s) instead of either the configured
 * value or the budget. A bean that never finishes stopping ({@link HangingManagementBeanConfig})
 * pins the management context's close duration to its timeout, which is how this test tells the two
 * apart.
 *
 * <p>The hanging bean is wired in only for this test, via {@code
 * test.hang-management-shutdown=true}, through the same {@code @ManagementContextConfiguration}
 * extension point the production fix uses, so it can't slow down every other test's context.
 *
 * <p>Started the way {@code main} starts it, like {@link ApiKeyStartupIT}, rather than through
 * {@code @SpringBootTest}: closing a Spring Test-managed context mid-test conflicts with the test
 * framework's own context caching, which expects the context to still be usable afterward.
 */
class ShutdownTimeoutBudgetIT {

  /**
   * The management share of the "web shutdown" budget from #27, plus margin against the 10 s
   * framework default this test guards against.
   */
  static final Duration BOUND = Duration.ofSeconds(5);

  @Test
  void managementContextShutdownStaysWithinBudgetEvenWhenABeanNeverFinishesStopping() {
    String[] args = {
      "--server.port=0",
      "--management.server.port=0",
      "--app.api-key=" + TestApiKey.VALUE,
      "--test.hang-management-shutdown=true"
    };
    ConfigurableApplicationContext context =
        SpringApplication.from(UrlShortenerApplication::main)
            .with(TestcontainersConfiguration.class)
            .run(args)
            .getApplicationContext();

    Instant start = Instant.now();
    context.close();

    assertThat(Duration.between(start, Instant.now())).isLessThan(BOUND);
  }
}
