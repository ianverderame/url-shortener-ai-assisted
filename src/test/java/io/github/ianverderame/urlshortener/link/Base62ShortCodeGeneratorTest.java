package io.github.ianverderame.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Base62ShortCodeGeneratorTest {

  private static final String BASE62 =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  private final ShortCodeGenerator generator = new Base62ShortCodeGenerator();

  @Test
  void codesAreEightBase62Characters() {
    for (int i = 0; i < 1_000; i++) {
      assertThat(generator.generate()).matches("[0-9A-Za-z]{8}");
    }
  }

  @Test
  void codesDoNotRepeat() {
    // 1,000 draws from 62^8 codes: a repeat has a probability of about 2e-9.
    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 1_000; i++) {
      codes.add(generator.generate());
    }
    assertThat(codes).hasSize(1_000);
  }

  @Test
  void codesUseTheWholeAlphabet() {
    // 80,000 characters: each symbol is expected about 1,290 times, so a miss means a bias.
    Set<Character> seen = new HashSet<>();
    for (int i = 0; i < 10_000; i++) {
      generator.generate().chars().forEach(c -> seen.add((char) c));
    }
    assertThat(seen).hasSize(BASE62.length());
  }
}
