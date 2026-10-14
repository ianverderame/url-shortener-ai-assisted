package io.github.ianverderame.urlshortener.link;

import java.util.Optional;

/**
 * Finds a link by its short code: the read access other packages get. Creating links goes through
 * this package's own service, which applies the destination policy first.
 */
public interface LinkLookup {

  Optional<Link> findByCode(String code);
}
