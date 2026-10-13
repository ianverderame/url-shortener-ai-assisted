package io.github.ianverderame.urlshortener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The configured API key, validated at startup so a misconfigured service refuses to start. Only
 * its SHA-256 digest is kept, and a presented key is compared digest to digest in constant time:
 * both sides are always 32 bytes, so neither the comparison time nor the key length leaks anything.
 * Messages never contain the key.
 */
@Component
class ApiKey {

  private static final String PROPERTY = "app.api-key";
  private static final int MIN_LENGTH = 32;
  private static final String LOCAL_PROFILE = "local";

  /** The published local demo key (compose.yaml, README). Not a secret, and only valid locally. */
  private static final String DEMO_KEY = "local-demo-key-NOT-A-SECRET-do-not-use-elsewhere";

  private final byte[] digest;

  ApiKey(@Value("${" + PROPERTY + ":}") String configured, Environment environment) {
    if (configured == null || configured.isBlank()) {
      throw invalid("is missing");
    }
    // Stripped, so stray whitespace (an env file, a copy-paste) can't smuggle the demo key past.
    if (configured.strip().equals(DEMO_KEY) && !environment.matchesProfiles(LOCAL_PROFILE)) {
      throw invalid("is the published demo key, which is only allowed under the local profile");
    }
    // Tomcat strips surrounding whitespace from header values and decodes them as ISO-8859-1, so a
    // key with surrounding whitespace (a secret file's trailing newline) or non-ASCII could never
    // match. Inner whitespace is refused too, so the key is always one plain header token.
    if (!configured.chars().allMatch(c -> c >= '!' && c <= '~')) {
      throw invalid("must contain only printable ASCII characters, with no whitespace");
    }
    if (configured.length() < MIN_LENGTH) {
      throw invalid("must be at least " + MIN_LENGTH + " characters");
    }
    this.digest = sha256(configured);
  }

  boolean matches(String presented) {
    if (presented == null) {
      return false;
    }
    return MessageDigest.isEqual(digest, sha256(presented));
  }

  @Override
  public String toString() {
    return "ApiKey[redacted]";
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      // Every Java platform is required to support SHA-256.
      throw new IllegalStateException(e);
    }
  }

  private static IllegalArgumentException invalid(String reason) {
    return new IllegalArgumentException(PROPERTY + " " + reason);
  }
}
