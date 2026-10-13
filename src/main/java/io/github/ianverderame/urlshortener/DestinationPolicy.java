package io.github.ianverderame.urlshortener;

import static io.github.ianverderame.urlshortener.RejectionReason.MALFORMED_URL;
import static io.github.ianverderame.urlshortener.RejectionReason.MISSING_HOST;
import static io.github.ianverderame.urlshortener.RejectionReason.NON_ASCII_HOST;
import static io.github.ianverderame.urlshortener.RejectionReason.SELF_HOST;
import static io.github.ianverderame.urlshortener.RejectionReason.TOO_LONG;
import static io.github.ianverderame.urlshortener.RejectionReason.UNSUPPORTED_SCHEME;
import static io.github.ianverderame.urlshortener.RejectionReason.USERINFO_NOT_ALLOWED;
import static io.github.ianverderame.urlshortener.RejectionReason.WHITESPACE_OR_CONTROL;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * The destination URL policy: returns the normalized destination, or rejects it with a specific
 * {@link RejectionReason}. Checks run in the reasons' precedence order, so the first broken rule is
 * the one reported.
 *
 * <p>Normalization lowercases the scheme and host and changes nothing else, so the result is the
 * submitted string modulo ASCII case, and feeding it back in returns it unchanged. Anything that
 * would need re-encoding is rejected instead.
 *
 * <p>The structural parse is {@link URI}; the scheme and authority are only located by their RFC
 * 3986 delimiters, so a spec-named reason can be reported even where the JDK refuses the input. The
 * JDK is lenient about some hosts and ports (decimal IPv4, zone ids, out-of-range or empty ports),
 * so those are checked explicitly.
 *
 * <p>IP-literal hosts are accepted deliberately: the service never fetches destinations, so there
 * is no SSRF exposure. An IP or another name for this shortener's own host passes the self-host
 * check; that loop is a documented limitation. No DNS lookups are made.
 */
@Component
class DestinationPolicy {

  static final int MAX_LENGTH = 2048;

  private static final int MAX_HOST_LENGTH = 253;
  private static final int MAX_LABEL_LENGTH = 63;
  private static final int MAX_PORT = 65535;

  /** The shortener's own host in comparable form: lowercased, one trailing dot removed. */
  private final String selfHost;

  DestinationPolicy(PublicBaseUrl publicBaseUrl) {
    this.selfHost = comparable(publicBaseUrl.host());
  }

  String normalize(String destination) {
    if (destination == null) {
      throw new InvalidDestinationException("Destination is required.");
    }
    if (destination.length() > MAX_LENGTH) {
      throw new InvalidDestinationException(TOO_LONG);
    }
    if (destination.codePoints().anyMatch(DestinationPolicy::isWhitespaceOrControl)) {
      throw new InvalidDestinationException(WHITESPACE_OR_CONTROL);
    }
    int colon = destination.indexOf(':');
    String scheme = asciiLowercase(destination.substring(0, Math.max(colon, 0)));
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new InvalidDestinationException(UNSUPPORTED_SCHEME);
    }
    if (!destination.startsWith("//", colon + 1)) {
      throw new InvalidDestinationException(MISSING_HOST);
    }
    int authorityStart = colon + 3;
    String authority = destination.substring(authorityStart, authorityEnd(destination, colon));
    // The host follows any userinfo and precedes any port; with neither, it is all that remains.
    String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
    if (hostAndPort.isEmpty() || hostAndPort.charAt(0) == ':') {
      throw new InvalidDestinationException(MISSING_HOST);
    }
    if (authority.indexOf('@') >= 0) {
      throw new InvalidDestinationException(USERINFO_NOT_ALLOWED);
    }
    int hostEnd = hostEnd(authority);
    String host = authority.substring(0, hostEnd);
    if (!isAscii(host) || hasPunycodeLabel(host)) {
      throw new InvalidDestinationException(NON_ASCII_HOST);
    }
    if (comparable(host).equals(selfHost)) {
      throw new InvalidDestinationException(SELF_HOST);
    }
    if (!isAscii(destination)
        || !parsesWithAHost(destination)
        || !isValidHost(host)
        || !isValidPortSuffix(authority.substring(hostEnd))) {
      throw new InvalidDestinationException(MALFORMED_URL);
    }
    int prefixEnd = authorityStart + hostEnd;
    return asciiLowercase(destination.substring(0, prefixEnd)) + destination.substring(prefixEnd);
  }

  /** Unicode White_Space, controls (Cc) and invisible format characters (Cf). */
  private static boolean isWhitespaceOrControl(int codePoint) {
    int type = Character.getType(codePoint);
    return Character.isWhitespace(codePoint)
        || Character.isSpaceChar(codePoint)
        || type == Character.CONTROL
        || type == Character.FORMAT;
  }

  private static int authorityEnd(String destination, int colon) {
    for (int i = colon + 3; i < destination.length(); i++) {
      char c = destination.charAt(i);
      if (c == '/' || c == '?' || c == '#') {
        return i;
      }
    }
    return destination.length();
  }

  /**
   * End of the host within the authority: past the bracket for IPv6 (0 if it is unbalanced, which
   * the JDK parse then rejects), else before any port.
   */
  private static int hostEnd(String authority) {
    if (authority.charAt(0) == '[') {
      return authority.indexOf(']') + 1;
    }
    return (authority + ":").indexOf(':'); // the whole authority when there is no port
  }

  private static boolean hasPunycodeLabel(String host) {
    for (String label : host.split("\\.", -1)) {
      if (asciiLowercase(label).startsWith("xn--")) {
        return true;
      }
    }
    return false;
  }

  private static boolean parsesWithAHost(String destination) {
    try {
      return new URI(destination).getHost() != null;
    } catch (URISyntaxException e) {
      return false;
    }
  }

  /**
   * Host rules the JDK does not enforce; it has already required letter-digit-hyphen labels with no
   * empty label and no leading or trailing hyphen, and validated any IPv6 text.
   */
  private static boolean isValidHost(String host) {
    if (host.startsWith("[")) {
      return !host.contains("%"); // zone ids
    }
    String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    if (name.length() > MAX_HOST_LENGTH) {
      return false;
    }
    String[] labels = name.split("\\.", -1);
    for (String label : labels) {
      if (label.length() > MAX_LABEL_LENGTH) {
        return false;
      }
    }
    // A numeric last label makes browsers parse the host as IPv4, so only a plain quad is allowed.
    return !looksNumeric(labels[labels.length - 1]) || isDottedQuad(labels);
  }

  /** Decimal digits, or "0x" and hex digits: what a browser's IPv4 parser reads as a number. */
  private static boolean looksNumeric(String label) {
    String lower = asciiLowercase(label);
    return isDigits(lower) || (lower.startsWith("0x") && isHexDigits(lower.substring(2)));
  }

  private static boolean isHexDigits(String s) {
    return s.chars().allMatch(HexFormat::isHexDigit);
  }

  private static boolean isDottedQuad(String[] labels) {
    if (labels.length != 4) {
      return false;
    }
    for (String octet : labels) {
      if (!isCanonicalNumber(octet) || Integer.parseInt(octet) > 255) {
        return false;
      }
    }
    return true;
  }

  /**
   * Empty, or a colon and a port from 1 to 65535 without leading zeros. Anything else after the
   * host has already failed the JDK parse.
   */
  private static boolean isValidPortSuffix(String suffix) {
    if (suffix.isEmpty()) {
      return true;
    }
    String port = suffix.substring(1);
    return isCanonicalNumber(port)
        && port.length() <= 5
        && Integer.parseInt(port) >= 1
        && Integer.parseInt(port) <= MAX_PORT;
  }

  /** Decimal digits with no leading zero (a lone "0" is allowed). */
  private static boolean isCanonicalNumber(String s) {
    return !s.isEmpty() && isDigits(s) && (s.length() == 1 || s.charAt(0) != '0');
  }

  private static boolean isDigits(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  private static boolean isAscii(String s) {
    return StandardCharsets.US_ASCII.newEncoder().canEncode(s);
  }

  /**
   * Lowercases A-Z only. Locale-independent, and it leaves non-ASCII alone, unlike {@link
   * String#toLowerCase()} (which maps the Kelvin sign to "k" and, in Turkish, "I" to a dotless i).
   */
  private static String asciiLowercase(String s) {
    StringBuilder lower = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      lower.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
    }
    return lower.toString();
  }

  private static String comparable(String host) {
    String lower = asciiLowercase(host);
    return lower.endsWith(".") ? lower.substring(0, lower.length() - 1) : lower;
  }
}
