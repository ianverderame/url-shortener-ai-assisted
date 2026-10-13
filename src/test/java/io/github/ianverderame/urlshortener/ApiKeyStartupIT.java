package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Issue #5: the real application, started the way {@code main} starts it (with containerized
 * PostgreSQL), refuses to start on an unusable API key and never prints the key while doing so.
 */
@ExtendWith(OutputCaptureExtension.class)
class ApiKeyStartupIT {

  private static final String DEMO_KEY = "local-demo-key-NOT-A-SECRET-do-not-use-elsewhere";

  @Test
  void theApplicationRefusesToStartWithoutAKey(CapturedOutput output) {
    Throwable failure = startupFailure(null, null);

    assertThat(messages(failure)).anyMatch(m -> m.contains("app.api-key is missing"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"short-key-of-31-characters-xyzw", "", " "})
  void theApplicationRefusesToStartWithAShortOrBlankKey(String key, CapturedOutput output) {
    Throwable failure = startupFailure(key, null);

    assertThat(messages(failure)).anyMatch(m -> m.contains("app.api-key"));
    if (!key.isBlank()) {
      assertThat(messages(failure)).noneMatch(m -> m.contains(key));
      assertThat(output.getAll()).doesNotContain(key);
    }
  }

  @Test
  void theApplicationRefusesToStartWithTheDemoKeyOutsideLocal(CapturedOutput output) {
    Throwable failure = startupFailure(DEMO_KEY, null);

    assertThat(messages(failure)).anyMatch(m -> m.contains("demo key"));
    assertThat(messages(failure)).noneMatch(m -> m.contains(DEMO_KEY));
    assertThat(output.getAll()).doesNotContain(DEMO_KEY);
  }

  @Test
  void theApplicationStartsWithTheDemoKeyUnderLocal() {
    try (ConfigurableApplicationContext context = start(DEMO_KEY, "local")) {
      assertThat(context.isRunning()).isTrue();
    }
  }

  // Key and profile are always passed explicitly (empty for "none"): arguments override the
  // environment, so an exported APP_API_KEY or SPRING_PROFILES_ACTIVE can't change the outcome.
  private static ConfigurableApplicationContext start(String apiKey, String profile) {
    String[] args = {
      "--server.port=0",
      "--management.server.port=0",
      "--app.api-key=" + (apiKey == null ? "" : apiKey),
      "--spring.profiles.active=" + (profile == null ? "" : profile)
    };
    return SpringApplication.from(UrlShortenerApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args)
        .getApplicationContext();
  }

  private static Throwable startupFailure(String apiKey, String profile) {
    Throwable failure =
        catchThrowable(
            () -> {
              // Started when it should not have: release its ports and pool, then fail.
              start(apiKey, profile).close();
              fail("The application started");
            });
    assertThat(failure).isNotNull().isNotInstanceOf(AssertionError.class);
    return failure;
  }

  private static List<String> messages(Throwable failure) {
    List<String> messages = new ArrayList<>();
    for (Throwable t = failure; t != null; t = t.getCause()) {
      messages.add(String.valueOf(t.getMessage()));
    }
    return messages;
  }
}
