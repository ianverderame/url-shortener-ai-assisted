package io.github.ianverderame.urlshortener;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The configured public base URL that short URLs are built from, never request headers. Validated
 * at startup so a bad value fails fast instead of producing broken links.
 */
@Component
class PublicBaseUrl {

  private static final String PROPERTY = "app.public-base-url";

  private final String base;
  private final String host;

  PublicBaseUrl(@Value("${" + PROPERTY + "}") String configured) {
    URI uri;
    try {
      uri = new URI(configured.strip());
    } catch (URISyntaxException e) {
      throw invalid("is not a valid URL");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw invalid("must use http or https");
    }
    if (uri.getHost() == null) {
      throw invalid("must include a host");
    }
    if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
      throw invalid("must not have a query or fragment");
    }
    this.base = uri.toString().replaceAll("/+$", "");
    this.host = uri.getHost();
  }

  /** The host as configured, for the destination policy's self-host check. */
  String host() {
    return host;
  }

  String shortUrl(String code) {
    return base + "/" + code;
  }

  private static IllegalArgumentException invalid(String reason) {
    return new IllegalArgumentException(PROPERTY + " " + reason);
  }
}
