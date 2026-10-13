package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublicBaseUrlTest {

  @Test
  void theShortUrlIsTheBasePlusTheCode() {
    assertThat(new PublicBaseUrl("https://sho.rt").shortUrl("Ab3dEf9h"))
        .isEqualTo("https://sho.rt/Ab3dEf9h");
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://sho.rt/", "https://sho.rt//"})
  void trailingSlashesOnTheBaseAreIgnored(String base) {
    assertThat(new PublicBaseUrl(base).shortUrl("Ab3dEf9h")).isEqualTo("https://sho.rt/Ab3dEf9h");
  }

  @Test
  void aBaseWithAPathKeepsIt() {
    assertThat(new PublicBaseUrl("https://example.com/s/").shortUrl("Ab3dEf9h"))
        .isEqualTo("https://example.com/s/Ab3dEf9h");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        " ",
        "sho.rt",
        "ftp://sho.rt",
        "https:///path",
        "https://sho.rt/?q=1",
        "https://sho.rt/#top",
        "not a url"
      })
  void anUnusableBaseIsRejectedAtStartup(String base) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PublicBaseUrl(base))
        .withMessageContaining("app.public-base-url");
  }
}
