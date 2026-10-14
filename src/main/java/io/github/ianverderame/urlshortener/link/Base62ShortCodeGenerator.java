package io.github.ianverderame.urlshortener.link;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
class Base62ShortCodeGenerator implements ShortCodeGenerator {

  private static final String ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final int CODE_LENGTH = 8;

  private final SecureRandom random = new SecureRandom();

  @Override
  public String generate() {
    StringBuilder code = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }
}
