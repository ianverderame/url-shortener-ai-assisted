package io.github.ianverderame.urlshortener.link;

/** Every attempt to insert a link under a fresh short code hit an existing code. */
public class CodeGenerationExhaustedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CodeGenerationExhaustedException() {
    super("Could not allocate a short code. Try again.");
  }
}
