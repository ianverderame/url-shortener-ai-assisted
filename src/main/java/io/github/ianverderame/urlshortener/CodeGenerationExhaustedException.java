package io.github.ianverderame.urlshortener;

/** Every attempt to insert a link under a fresh short code hit an existing code. */
class CodeGenerationExhaustedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  CodeGenerationExhaustedException() {
    super("Could not allocate a short code. Try again.");
  }
}
