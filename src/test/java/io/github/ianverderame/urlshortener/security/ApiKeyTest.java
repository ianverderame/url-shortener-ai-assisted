package io.github.ianverderame.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class ApiKeyTest {

  private static final String KEY = "0123456789abcdef0123456789abcdef-unit";
  private static final String DEMO_KEY = "local-demo-key-NOT-A-SECRET-do-not-use-elsewhere";

  private final MockEnvironment noProfile = new MockEnvironment();

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void aMissingKeyIsRejected(String configured) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiKey(configured, noProfile))
        .withMessageContaining("app.api-key")
        .withMessageContaining("missing");
  }

  @Test
  void aKeyUnder32CharactersIsRejectedWithoutPrintingIt() {
    String shortKey = "only-31-characters-long-key-xyz";
    assertThat(shortKey).hasSize(31);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiKey(shortKey, noProfile))
        .withMessageContaining("32 characters")
        .withMessageNotContaining(shortKey);
  }

  // Tomcat strips surrounding whitespace from header values and decodes them as ISO-8859-1, so a
  // configured key with whitespace or non-ASCII characters could never be presented.
  @ParameterizedTest
  @ValueSource(
      strings = {
        // a secret file's trailing newline; 31 characters plus the newline also passes the length
        "0123456789abcdef0123456789abcde\n",
        " 0123456789abcdef0123456789abcdef",
        "0123456789abcdef0123456789abcdef\t",
        "0123456789abcdef 0123456789abcdef",
        "0123456789abcdef0123456789abcdefé",
        "0123456789abcdef0123456789abcdef\u0000",
        "0123456789abcdef0123456789abcdef\u007f"
      })
  void aKeyWithWhitespaceOrNonPrintableCharactersIsRejectedWithoutPrintingIt(String configured) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiKey(configured, noProfile))
        .withMessageContaining("app.api-key")
        .withMessageContaining("printable ASCII")
        .withMessageNotContaining(configured.strip());
  }

  @Test
  void theFirstAndLastPrintableAsciiCharactersAreAllowed() {
    String key = "!0123456789abcdef0123456789abcdef~";
    assertThat(new ApiKey(key, noProfile).matches(key)).isTrue();
  }

  @Test
  void aKeyOfExactly32CharactersIsAccepted() {
    String key = "a".repeat(32);
    assertThat(new ApiKey(key, noProfile).matches(key)).isTrue();
  }

  @Test
  void theDemoKeyIsRejectedOutsideTheLocalProfileWithoutPrintingIt() {
    MockEnvironment prod = new MockEnvironment();
    prod.setActiveProfiles("prod");

    for (MockEnvironment environment : new MockEnvironment[] {noProfile, prod}) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new ApiKey(DEMO_KEY, environment))
          .withMessageContaining("demo key")
          .withMessageNotContaining(DEMO_KEY);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "\t", "\n"})
  void theDemoKeyPaddedWithWhitespaceIsStillTheDemoKey(String padding) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiKey(padding + DEMO_KEY + padding, noProfile))
        .withMessageContaining("demo key");
  }

  @Test
  void theDemoKeyIsAcceptedUnderTheLocalProfile() {
    MockEnvironment local = new MockEnvironment();
    local.setActiveProfiles("local");

    assertThat(new ApiKey(DEMO_KEY, local).matches(DEMO_KEY)).isTrue();
  }

  @Test
  void theConfiguredKeyMatches() {
    assertThat(new ApiKey(KEY, noProfile).matches(KEY)).isTrue();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        // same length, last character differs
        "0123456789abcdef0123456789abcdef-unix",
        // shorter: a prefix of the key
        "0123456789abcdef0123456789abcdef",
        // longer: the key with a suffix
        "0123456789abcdef0123456789abcdef-unit-extra",
        // different case
        "0123456789ABCDEF0123456789ABCDEF-UNIT"
      })
  void anyOtherPresentedValueDoesNotMatch(String presented) {
    assertThat(new ApiKey(KEY, noProfile).matches(presented)).isFalse();
  }

  @Test
  void theKeyIsNotExposedThroughToString() {
    assertThat(new ApiKey(KEY, noProfile).toString()).isEqualTo("ApiKey[redacted]");
  }
}
