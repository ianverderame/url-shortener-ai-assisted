package io.github.ianverderame.urlshortener.link;

import java.time.Instant;
import java.util.Optional;

/** Where links are stored. */
interface LinkRepository extends LinkLookup {

  /** Inserts the link, or returns empty without inserting if the code is already taken. */
  Optional<Link> insertIfCodeFree(String code, String destination, Instant createdAt);
}
