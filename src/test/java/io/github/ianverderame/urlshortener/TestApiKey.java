package io.github.ianverderame.urlshortener;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * The API key integration tests run with: generated per test JVM, so no test key lives in the repo.
 * Test classes register it with {@code @DynamicPropertySource}.
 */
final class TestApiKey {

  static final String VALUE = generate();

  private TestApiKey() {}

  static void register(DynamicPropertyRegistry registry) {
    registry.add("app.api-key", () -> VALUE);
  }

  private static String generate() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
