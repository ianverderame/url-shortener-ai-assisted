package io.github.ianverderame.urlshortener;

import static io.github.ianverderame.urlshortener.RejectionReason.MALFORMED_URL;
import static io.github.ianverderame.urlshortener.RejectionReason.MISSING_HOST;
import static io.github.ianverderame.urlshortener.RejectionReason.NON_ASCII_HOST;
import static io.github.ianverderame.urlshortener.RejectionReason.SELF_HOST;
import static io.github.ianverderame.urlshortener.RejectionReason.TOO_LONG;
import static io.github.ianverderame.urlshortener.RejectionReason.UNSUPPORTED_SCHEME;
import static io.github.ianverderame.urlshortener.RejectionReason.USERINFO_NOT_ALLOWED;
import static io.github.ianverderame.urlshortener.RejectionReason.WHITESPACE_OR_CONTROL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The adversarial case table for the destination URL policy (issue #6). Rows and ids come from the
 * {@code spec-test-designer} draft posted on #6, written from PRD #1 and the issue only. Rows the
 * draft left open ({@code Qn}) follow the engineer's answers, recorded in the PR:
 *
 * <ul>
 *   <li>Q1: only the scheme and host are lowercased; everything else is kept as submitted.
 *   <li>Q2/Q9: precedence is TOO_LONG, WHITESPACE_OR_CONTROL, UNSUPPORTED_SCHEME, MISSING_HOST,
 *       USERINFO_NOT_ALLOWED, NON_ASCII_HOST, SELF_HOST, then MALFORMED_URL.
 *   <li>Q3: the limit is 2,048 UTF-16 units of the raw input.
 *   <li>Q4: format characters (Unicode Cf) are rejected as WHITESPACE_OR_CONTROL.
 *   <li>Q5: any other non-ASCII, or anything a strict JDK URI parse refuses, is MALFORMED_URL.
 *   <li>Q6: punycode ({@code xn--}) labels are NON_ASCII_HOST.
 *   <li>Q7/Q8: letter-digit-hyphen hostnames, dotted-quad IPv4 and zone-less IPv6 only; ports
 *       1-65535 without leading zeros. Everything else is MALFORMED_URL.
 *   <li>Q10: the base host is compared lowercased with one trailing dot removed.
 *   <li>Q11: whitespace is never trimmed. Q12: every accepted result is idempotent.
 * </ul>
 *
 * <p>The draft wrote some control characters in caret notation; they are decoded here from each
 * row's note and written as {@code \}{@code u} escapes.
 */
class DestinationPolicyTest {

  private static final String EXAMPLE = "https://example.com/";

  private final DestinationPolicy policy = policyFor("https://short.example");

  @ParameterizedTest(name = "{0}")
  @MethodSource("accepted")
  void anAcceptedDestinationNormalizesToTheExpectedString(
      String id, String input, String expected) {
    String normalized = policy.normalize(input);

    assertThat(normalized).isEqualTo(expected);
    assertThat(policy.normalize(normalized)).as("idempotent").isEqualTo(normalized);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejected")
  void aRejectedDestinationGivesItsReason(String id, String input, RejectionReason reason) {
    assertThat(rejectionOf(policy, input)).isEqualTo(reason);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejected")
  void aRejectionNeverEchoesTheDestination(String id, String input, RejectionReason reason) {
    InvalidDestinationException e =
        catchThrowableOfType(InvalidDestinationException.class, () -> policy.normalize(input));

    assertThat(e.getMessage()).isEqualTo(reason.detail()).doesNotContain("example");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("selfHostOnAPort")
  void theBasePortIsIgnored(String id, String input) {
    assertThat(rejectionOf(policyFor("https://short.example:8443"), input)).isEqualTo(SELF_HOST);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("localBaseRejected")
  void withALocalhostBaseEveryLocalhostDestinationIsRejected(String id, String input) {
    assertThat(rejectionOf(policyFor("http://localhost:8080"), input)).isEqualTo(SELF_HOST);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("localBaseAccepted")
  void aliasesOfALocalhostBaseAreADocumentedLimitation(String id, String input) {
    assertThat(policyFor("http://localhost:8080").normalize(input)).isEqualTo(input);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mixedCaseBase")
  void theBaseHostIsComparedLowercasedWithoutATrailingDot(String base) {
    assertThat(rejectionOf(policyFor(base), "https://short.example/x")).isEqualTo(SELF_HOST);
  }

  @Test
  void normalizationIgnoresATurkishDefaultLocale() { // NORM-09
    Locale original = Locale.getDefault();
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    try {
      assertThat(policy.normalize("https://MAIL.EXAMPLE/")).isEqualTo("https://mail.example/");
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void aNullDestinationIsRejectedWithoutAPolicyReason() {
    InvalidDestinationException e =
        catchThrowableOfType(InvalidDestinationException.class, () -> policy.normalize(null));

    assertThat(e.reason()).isNull();
  }

  static Stream<Arguments> accepted() {
    return Stream.of(
        // 1. Scheme
        accept("SCH-01", "http://example.com/", "http://example.com/"),
        accept("SCH-02", EXAMPLE, EXAMPLE),
        accept("SCH-03", "HTTPS://example.com/", EXAMPLE),
        accept("SCH-04", "HtTpS://example.com/", EXAMPLE),
        // 2. Length
        same("LEN-01", EXAMPLE + "a".repeat(2028)),
        same("LEN-03", EXAMPLE + "a".repeat(2027)),
        accept("LEN-04", "HTTPS://EXAMPLE.COM/" + "a".repeat(2028), EXAMPLE + "a".repeat(2028)),
        same("LEN-05", "https://example.com/?q=" + "a".repeat(2025)),
        // 3. Whitespace and control characters: encoded forms are not raw characters
        same("WS-29", "https://example.com/a%20b"),
        same("WS-30", "https://example.com/%0D%0ASet-Cookie:x=y"),
        same("WS-31", "https://example.com/%00"),
        // 4. Host required
        same("HOST-10", "https://nonexistent.invalid/"),
        same("HOST-11", "https://intranet/"),
        // 5. Userinfo: '@' outside the authority is not userinfo
        same("UI-14", "https://example.com/@evil.example"),
        same("UI-15", "https://example.com/path?next=user@evil.example"),
        same("UI-16", "https://example.com/#user@evil.example"),
        same("UI-17", "https://example.com/?a=b@c:d@e"),
        same("UI-18", "https://example.com/%40evil.example"),
        same("UI-19", "https://example.com/user:pass@x"),
        // 7. IP literals and localhost
        same("IP-01", "http://127.0.0.1/"),
        same("IP-02", "http://10.0.0.1:8080/admin"),
        same("IP-03", "http://169.254.169.254/latest/meta-data/"),
        same("IP-04", "http://0.0.0.0/"),
        same("IP-05", "http://[::1]/"),
        accept("IP-06", "http://[2001:DB8::A]:8080/x", "http://[2001:db8::a]:8080/x"),
        same("IP-07", "http://[::ffff:127.0.0.1]/"),
        same("IP-08", "http://localhost/"),
        accept("IP-09", "HTTP://LOCALHOST:3000/", "http://localhost:3000/"),
        same("IP-10", "http://[0:0:0:0:0:0:0:1]/"),
        // 8. Self-host: near misses are other hosts
        same("SELF-11", "https://sub.short.example/"),
        same("SELF-12", "https://www.short.example/"),
        same("SELF-13", "https://short.example.evil.example/"),
        same("SELF-14", "https://evilshort.example/"),
        same("SELF-15", "https://short.exampl/"),
        same("SELF-16", "https://short.example.com/"),
        same("SELF-17", "https://evil.example/short.example"),
        same("SELF-18", "https://evil.example/?u=https://short.example/abc12345"),
        same("SELF-19", "https://evil.example/#short.example"),
        // 9. Normalization: scheme and host lowercased, nothing else changed
        accept(
            "NORM-01",
            "https://Example.COM/Path/To?Query=Value#Frag",
            "https://example.com/Path/To?Query=Value#Frag"),
        accept("NORM-02", "HTTP://EXAMPLE.COM:8080/A", "http://example.com:8080/A"),
        same("NORM-03", "https://example.com/a%2Fb%20c"),
        same("NORM-04", "https://example.com/?q=%25"),
        same("NORM-05", "https://example.com/?b=2&a=1&b=3"),
        same("NORM-06", "https://example.com/a/b/"),
        same("NORM-07", "https://example.com/%E2%82%AC"),
        same("NORM-08", "https://example.com/~user/"),
        same("NORM-10", "https://example.com"),
        same("NORM-11", "https://example.com:443/"),
        same("NORM-12", "http://example.com:80/"),
        same("NORM-13", "https://example.com/a?"),
        same("NORM-14", "https://example.com/a#"),
        same("NORM-15", "https://example.com/a/./b/../c"),
        same("NORM-16", "https://example.com/%2e%2e/x"),
        same("NORM-17", "https://example.com/%7euser"),
        same("NORM-18", "https://example.com./"),
        // 10. Parser differentials: the host is example.com either way
        same("PD-12", "https://example.com#@evil.example"),
        same("PD-13", "https://example.com?@evil.example"),
        // Boundaries added in #6 for mutation testing (not in the spec-test-designer draft)
        accept("B-01 A and Z", "HTTPS://AZ.EXAMPLE/AZ", "https://az.example/AZ"),
        same("B-02 63-char label", "https://" + "a".repeat(63) + ".example/"),
        same("B-03 253-char host", "https://" + LONGEST_HOST + "/"),
        same("B-04 253 and a dot", "https://" + LONGEST_HOST + "./"),
        same("B-05 octet 255", "http://255.255.255.255/"),
        same("B-06 port 1", "http://example.com:1/"),
        same("B-07 port 65535", "http://example.com:65535/"),
        // pr-reviewer: only 0x plus hex digits reads as a number
        same("B-08 0x, not hex", "http://0xyz/"),
        same("B-09 0x hyphenated", "http://0x-proxy/"));
  }

  /** 63 + 1 + 63 + 1 + 63 + 1 + 61 = 253 characters, the DNS limit. */
  private static final String LONGEST_HOST =
      "a".repeat(63) + "." + "a".repeat(63) + "." + "a".repeat(63) + "." + "b".repeat(61);

  static Stream<Arguments> rejected() {
    return Stream.of(
        // 1. Scheme
        reject("SCH-05", "example.com", UNSUPPORTED_SCHEME),
        reject("SCH-06", "example.com/path", UNSUPPORTED_SCHEME),
        reject("SCH-07", "localhost:8080/x", UNSUPPORTED_SCHEME),
        reject("SCH-08", "//example.com/x", UNSUPPORTED_SCHEME),
        reject("SCH-09", "/abc12345", UNSUPPORTED_SCHEME),
        reject("SCH-10", "javascript:alert(1)", UNSUPPORTED_SCHEME),
        reject("SCH-11", "JavaScript:alert(1)", UNSUPPORTED_SCHEME),
        reject("SCH-12", "javascript://example.com/%0Aalert(1)", UNSUPPORTED_SCHEME),
        reject(
            "SCH-13",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            UNSUPPORTED_SCHEME),
        reject("SCH-14", "file:///etc/passwd", UNSUPPORTED_SCHEME),
        reject("SCH-15", "file://example.com/share", UNSUPPORTED_SCHEME),
        reject("SCH-16", "ftp://example.com/", UNSUPPORTED_SCHEME),
        reject("SCH-17", "ws://example.com/", UNSUPPORTED_SCHEME),
        reject("SCH-18", "wss://example.com/", UNSUPPORTED_SCHEME),
        reject("SCH-19", "httpx://example.com/", UNSUPPORTED_SCHEME),
        reject("SCH-20", "https2://example.com/", UNSUPPORTED_SCHEME),
        reject("SCH-21", "xhttp://example.com/", UNSUPPORTED_SCHEME),
        reject("SCH-22", "vbscript:msgbox(1)", UNSUPPORTED_SCHEME),
        reject("SCH-23", "blob:https://example.com/uuid", UNSUPPORTED_SCHEME),
        reject("SCH-24", "https:", MISSING_HOST),
        reject("SCH-25", "", UNSUPPORTED_SCHEME),
        reject("SCH-26 long s", "http\u017F://example.com/", UNSUPPORTED_SCHEME),
        // 2. Length
        reject("LEN-02", EXAMPLE + "a".repeat(2029), TOO_LONG),
        reject("LEN-06", "https://example.com/#" + "a".repeat(2028), TOO_LONG),
        reject("LEN-07", EXAMPLE + "a".repeat(100_000), TOO_LONG),
        reject("LEN-08", "https://example.com:443/" + "a".repeat(2025), TOO_LONG),
        reject("LEN-09", EXAMPLE + "a".repeat(2027) + "\u00E9", MALFORMED_URL),
        reject("LEN-10", EXAMPLE + "a".repeat(2026) + "\uD83D\uDE00", MALFORMED_URL),
        // 3. Whitespace and control characters, never trimmed
        reject("WS-01", " https://example.com/", WHITESPACE_OR_CONTROL),
        reject("WS-02", "https://example.com/ ", WHITESPACE_OR_CONTROL),
        reject("WS-03", "https://example.com/\n", WHITESPACE_OR_CONTROL),
        reject("WS-04", "\r\nhttps://example.com/", WHITESPACE_OR_CONTROL),
        reject("WS-05", "\thttps://example.com/", WHITESPACE_OR_CONTROL),
        reject("WS-06", "https://exa mple.com/", WHITESPACE_OR_CONTROL),
        reject("WS-07", "https://example.com/a b", WHITESPACE_OR_CONTROL),
        reject("WS-08", "https://example.com/?q=a b", WHITESPACE_OR_CONTROL),
        reject("WS-09", "https://example.com/#a b", WHITESPACE_OR_CONTROL),
        reject("WS-10", "https://example.com/a\tb", WHITESPACE_OR_CONTROL),
        reject("WS-11", "https://example.com/\r\nSet-Cookie:x=y", WHITESPACE_OR_CONTROL),
        reject("WS-12", "https://example.com/\u0000", WHITESPACE_OR_CONTROL),
        reject("WS-13", "https://example.com\u0000.evil.example/", WHITESPACE_OR_CONTROL),
        reject("WS-14", "https://example.com/\u007F", WHITESPACE_OR_CONTROL),
        reject("WS-15", "https://example.com/\u001B[0m", WHITESPACE_OR_CONTROL),
        reject("WS-16", "https://example.com/\u000B", WHITESPACE_OR_CONTROL),
        reject("WS-17", "https://example.com/\f", WHITESPACE_OR_CONTROL),
        reject("WS-18", "https://example.com/\u001F", WHITESPACE_OR_CONTROL),
        reject("WS-19", "https://example.com/\u0080", WHITESPACE_OR_CONTROL),
        reject("WS-20", "https://example.com/\u009F", WHITESPACE_OR_CONTROL),
        reject("WS-21", "https://example.com/\u0085", WHITESPACE_OR_CONTROL),
        reject("WS-22", "https://example.com/\u00A0", WHITESPACE_OR_CONTROL),
        reject("WS-23", "https://example.com/\u2007", WHITESPACE_OR_CONTROL),
        reject("WS-24", "https://example.com/\u202F", WHITESPACE_OR_CONTROL),
        reject("WS-25", "https://example.com/\u2028", WHITESPACE_OR_CONTROL),
        reject("WS-26", "https://example.com/\u2029", WHITESPACE_OR_CONTROL),
        reject("WS-27", "https://example.com/\u3000", WHITESPACE_OR_CONTROL),
        reject("WS-28", "https://example.com/\u1680", WHITESPACE_OR_CONTROL),
        reject("WS-32", "https://example.com/\u200B", WHITESPACE_OR_CONTROL),
        reject("WS-33", "https://example.com/\u202E", WHITESPACE_OR_CONTROL),
        reject("WS-34", "https://example.com/\u00AD", WHITESPACE_OR_CONTROL),
        reject("WS-35", "\uFEFFhttps://example.com/", WHITESPACE_OR_CONTROL),
        // 4. Host required
        reject("HOST-01", "http:/path", MISSING_HOST),
        reject("HOST-02", "http://", MISSING_HOST),
        reject("HOST-03", "http:///x", MISSING_HOST),
        reject("HOST-04", "https://:443/", MISSING_HOST),
        reject("HOST-05", "https://:/", MISSING_HOST),
        reject("HOST-06", "https:example.com", MISSING_HOST),
        reject("HOST-07", "https:/example.com", MISSING_HOST),
        reject("HOST-08", "https://?q=1", MISSING_HOST),
        reject("HOST-09", "https://#frag", MISSING_HOST),
        // 5. Userinfo
        reject("UI-01", "https://user@example.com/", USERINFO_NOT_ALLOWED),
        reject("UI-02", "https://user:pass@example.com/", USERINFO_NOT_ALLOWED),
        reject("UI-03", "https://trusted.example@evil.example/", USERINFO_NOT_ALLOWED),
        reject("UI-04", "https://@example.com/", USERINFO_NOT_ALLOWED),
        reject("UI-05", "https://:@example.com/", USERINFO_NOT_ALLOWED),
        reject("UI-06", "https://user:@example.com/", USERINFO_NOT_ALLOWED),
        reject("UI-07", "https://%40@example.com/", USERINFO_NOT_ALLOWED),
        reject("UI-08", "https://example.com:443@evil.example/", USERINFO_NOT_ALLOWED),
        reject("UI-09", "https://example.com;@evil.example/", USERINFO_NOT_ALLOWED),
        reject("UI-10", "HTTPS://USER@EXAMPLE.COM/", USERINFO_NOT_ALLOWED),
        reject("UI-11", "https://user@[::1]/", USERINFO_NOT_ALLOWED),
        reject("UI-12", "https://user@127.0.0.1/", USERINFO_NOT_ALLOWED),
        reject("UI-13", "https://a@b@evil.example/", USERINFO_NOT_ALLOWED),
        reject("UI-20", "https://example.com%40evil.example/", MALFORMED_URL),
        // 6. Non-ASCII hosts
        reject("IDN-01", "https://b\u00FCcher.example/", NON_ASCII_HOST),
        reject("IDN-02", "https://\u4F8B\u3048.jp/", NON_ASCII_HOST),
        reject("IDN-03", "https://ex\u0430mple.com/", NON_ASCII_HOST),
        reject("IDN-04", "https://\uFF45xample.com/", NON_ASCII_HOST),
        reject("IDN-05", "https://example\u3002com/", NON_ASCII_HOST),
        reject("IDN-06", "https://\u212Aelvin.example/", NON_ASCII_HOST),
        reject("IDN-07", "https://\u017Fhort.example/", NON_ASCII_HOST),
        reject("IDN-08", "https://B\u00DCCHER.example/", NON_ASCII_HOST),
        reject("IDN-09", "https://\u0130nfo.example/", NON_ASCII_HOST),
        reject("IDN-10", "http://\uFF11\uFF12\uFF17.0.0.1/", NON_ASCII_HOST),
        reject("IDN-11", "https://xn--bcher-kva.example/", NON_ASCII_HOST),
        reject("IDN-12", "https://xn--80ak6aa92e.com/", NON_ASCII_HOST),
        reject("IDN-12b upper", "https://XN--80AK6AA92E.com/", NON_ASCII_HOST),
        reject("IDN-13", "https://b%C3%BCcher.example/", MALFORMED_URL),
        reject("IDN-14", "https://example.com/caf\u00E9", MALFORMED_URL),
        // 7. Non-canonical IPv4 forms and zone ids
        reject("IP-11", "http://2130706433/", MALFORMED_URL),
        reject("IP-12", "http://0177.0.0.1/", MALFORMED_URL),
        reject("IP-13", "http://0x7f.0.0.1/", MALFORMED_URL),
        reject("IP-14", "http://127.1/", MALFORMED_URL),
        reject("IP-15", "http://256.256.256.256/", MALFORMED_URL),
        reject("IP-16", "http://[fe80::1%25eth0]/", MALFORMED_URL),
        reject("IP-17 hex last label", "http://example.0x7f/", MALFORMED_URL),
        reject("IP-18 single hex label", "http://0x7f/", MALFORMED_URL),
        reject("IP-19 bare 0x label", "http://0X/", MALFORMED_URL),
        // 8. Self-host (base https://short.example)
        reject("SELF-01", "https://short.example/abc12345", SELF_HOST),
        reject("SELF-02", "http://short.example/abc12345", SELF_HOST),
        reject("SELF-03", "https://SHORT.EXAMPLE/abc12345", SELF_HOST),
        reject("SELF-04", "https://Short.Example/", SELF_HOST),
        reject("SELF-05", "https://short.example./abc12345", SELF_HOST),
        reject("SELF-06", "https://short.example:8443/", SELF_HOST),
        reject("SELF-07", "https://short.example:443/", SELF_HOST),
        reject("SELF-08", "HTTPS://SHORT.EXAMPLE.:80/x", SELF_HOST),
        reject("SELF-09", "https://short.example", SELF_HOST),
        reject("SELF-10", "https://short.example?x=1", SELF_HOST),
        reject("SELF-20", "https://short.example@evil.example/", USERINFO_NOT_ALLOWED),
        reject("SELF-21", "https://evil.example@short.example/", USERINFO_NOT_ALLOWED),
        reject("SELF-22", "https://short.example../", MALFORMED_URL),
        reject("SELF-23", "https://short%2Eexample/", MALFORMED_URL),
        reject("SELF-24", "https://short.example:/", SELF_HOST),
        // 9. Ports
        reject("NORM-19", "https://example.com:08443/", MALFORMED_URL),
        reject("NORM-20", "https://example.com:65536/", MALFORMED_URL),
        reject("NORM-21", "https://example.com:0/", MALFORMED_URL),
        reject("NORM-22", "https://example.com:/", MALFORMED_URL),
        // 10. Parser differentials and strict syntax
        reject("PD-01", "ht\ttps://example.com/", WHITESPACE_OR_CONTROL),
        reject("PD-02", "https://exa\nmple.com/", WHITESPACE_OR_CONTROL),
        reject("PD-03", "https://example.com\\@evil.example/", USERINFO_NOT_ALLOWED),
        reject("PD-04", "https:\\\\evil.example/", MISSING_HOST),
        reject("PD-05", "https://example.com/a\\b", MALFORMED_URL),
        reject("PD-06", "https://[::1/", MALFORMED_URL),
        reject("PD-07", "https://::1]/", MISSING_HOST), // draft: MALFORMED_URL; the host is empty
        reject("PD-08", "https://[::1]]/", MALFORMED_URL),
        reject("PD-09", "https://[::1]x/", MALFORMED_URL),
        reject("PD-10", "https://[example.com]/", MALFORMED_URL),
        reject("PD-11", "https://example.com:80:80/", MALFORMED_URL),
        reject("PD-14", "https://" + "a".repeat(64) + ".example/", MALFORMED_URL),
        reject(
            "PD-15", "https://" + (("a".repeat(63) + ".").repeat(4)) + "example/", MALFORMED_URL),
        reject("PD-16", "https://exa_mple.com/", MALFORMED_URL),
        reject("PD-17", "https://example..com/", MALFORMED_URL),
        reject("PD-18", "https://-example.com/", MALFORMED_URL),
        reject("PD-18b trailing hyphen", "https://example-.com/", MALFORMED_URL),
        reject("PD-19", "https://example.com/|x", MALFORMED_URL),
        reject("PD-20", "https://example.com/<script>", MALFORMED_URL),
        reject("PD-21", "https://example.com/\"onmouseover=x", MALFORMED_URL),
        reject("PD-22", "https://example.com/#a#b", MALFORMED_URL),
        reject("PD-23", "https://example.com/%", MALFORMED_URL),
        reject("PD-24", "https://example.com/%zz", MALFORMED_URL),
        // Boundaries added in #6 for mutation testing (not in the spec-test-designer draft)
        reject("B-10 254-char host", "https://a" + LONGEST_HOST + "/", MALFORMED_URL),
        // pr-reviewer: an empty host is MISSING_HOST whatever follows it (named reason wins)
        reject("B-11 junk port, no host", "https://:é/", MISSING_HOST),
        reject("B-12 non-digit port, no host", "https://:443x/", MISSING_HOST),
        reject("B-13 userinfo, no host", "https://user@/", MISSING_HOST),
        reject("B-14 empty userinfo, no host", "https://@/", MISSING_HOST),
        reject("B-15 userinfo, end of input", "https://user@", MISSING_HOST),
        reject("B-16 userinfo, port only", "https://user@:443/", MISSING_HOST));
  }

  @Test
  void aBracketedBaseHostIsMatchedOnAnyPort() {
    assertThat(rejectionOf(policyFor("http://[::1]:8080"), "http://[::1]:9000/"))
        .isEqualTo(SELF_HOST);
  }

  @Test
  void everyReasonHasItsOwnFixedDetail() {
    assertThat(RejectionReason.values())
        .extracting(RejectionReason::detail)
        .doesNotHaveDuplicates()
        .allSatisfy(detail -> assertThat(detail).startsWith("Destination "));
  }

  static Stream<Arguments> selfHostOnAPort() {
    return Stream.of(
        Arguments.of("SELF-30", "https://short.example/x"),
        Arguments.of("SELF-31", "http://short.example:8080/x"));
  }

  static Stream<Arguments> localBaseRejected() {
    return Stream.of(
        Arguments.of("SELF-40", "http://localhost:8080/abc12345"),
        Arguments.of("SELF-41", "http://localhost:3000/"),
        Arguments.of("SELF-42", "http://LOCALHOST./"),
        Arguments.of("SELF-43", "https://localhost/"));
  }

  static Stream<Arguments> localBaseAccepted() {
    return Stream.of(
        Arguments.of("SELF-44", "http://127.0.0.1:8080/abc12345"),
        Arguments.of("SELF-45", "http://[::1]:8080/abc12345"),
        Arguments.of("SELF-46", "http://localhost.localdomain:8080/"));
  }

  static Stream<String> mixedCaseBase() {
    return Stream.of("https://Short.Example", "https://short.example.", "HTTPS://SHORT.EXAMPLE.");
  }

  private static Arguments accept(String id, String input, String expected) {
    return Arguments.of(id, input, expected);
  }

  private static Arguments same(String id, String input) {
    return Arguments.of(id, input, input);
  }

  private static Arguments reject(String id, String input, RejectionReason reason) {
    return Arguments.of(id, input, reason);
  }

  private static DestinationPolicy policyFor(String base) {
    return new DestinationPolicy(new PublicBaseUrl(base));
  }

  private static RejectionReason rejectionOf(DestinationPolicy policy, String input) {
    InvalidDestinationException e =
        catchThrowableOfType(InvalidDestinationException.class, () -> policy.normalize(input));
    assertThat(e).as("rejected").isNotNull();
    return e.reason();
  }
}
