package io.github.ianverderame.urlshortener;

/**
 * Why the destination URL policy rejected a destination, in precedence order: when an input breaks
 * several rules, the first one listed here is reported. Details are fixed, safe strings that never
 * contain the submitted URL.
 */
enum RejectionReason {
  TOO_LONG("Destination must be at most 2048 characters."),
  WHITESPACE_OR_CONTROL("Destination must not contain whitespace, control or format characters."),
  UNSUPPORTED_SCHEME("Destination scheme must be http or https."),
  MISSING_HOST("Destination must include a host."),
  USERINFO_NOT_ALLOWED("Destination must not include user information."),
  NON_ASCII_HOST("Destination host must be ASCII; internationalized domains are not supported."),
  SELF_HOST("Destination must not point at this shortener."),
  MALFORMED_URL("Destination is not a valid URL.");

  private final String detail;

  RejectionReason(String detail) {
    this.detail = detail;
  }

  String detail() {
    return detail;
  }
}
