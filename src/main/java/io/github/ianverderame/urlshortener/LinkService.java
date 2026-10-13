package io.github.ianverderame.urlshortener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
class LinkService {

  /** Attempts per create; with 62^8 codes, more than one conflict in a row is an anomaly. */
  private static final int MAX_CODE_ATTEMPTS = 3;

  private static final String EXHAUSTION_METRIC = "links.code.generation.exhausted";

  private final JdbcClient jdbcClient;
  private final TransactionOperations transactions;
  private final ShortCodeGenerator shortCodeGenerator;
  private final Clock clock;
  private final Counter exhaustionCounter;

  LinkService(
      JdbcClient jdbcClient,
      TransactionOperations transactions,
      ShortCodeGenerator shortCodeGenerator,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.jdbcClient = jdbcClient;
    this.transactions = transactions;
    this.shortCodeGenerator = shortCodeGenerator;
    this.clock = clock;
    this.exhaustionCounter =
        Counter.builder(EXHAUSTION_METRIC)
            .description("Link creations that gave up after repeated short-code conflicts")
            .register(meterRegistry);
  }

  /**
   * Inserts the link under a fresh code. Each attempt runs in its own transaction, and only a code
   * conflict (an empty {@code RETURNING}) leads to another attempt.
   */
  Link create(String destination) {
    validate(destination);
    for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
      String code = shortCodeGenerator.generate();
      Instant createdAt = Instant.now(clock);
      Optional<Link> inserted =
          transactions.execute(status -> insert(code, destination, createdAt));
      if (inserted.isPresent()) {
        return inserted.get();
      }
    }
    exhaustionCounter.increment();
    throw new CodeGenerationExhaustedException();
  }

  private Optional<Link> insert(String code, String destination, Instant createdAt) {
    return jdbcClient
        .sql(
            "INSERT INTO links (code, destination, created_at) "
                + "VALUES (:code, :destination, :createdAt) "
                + "ON CONFLICT (code) DO NOTHING "
                + "RETURNING id, code, destination, created_at")
        .param("code", code)
        .param("destination", destination)
        .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
        .query(Link.class)
        .optional();
  }

  /** Minimal check for the tracer bullet: http(s) with a host. The full policy is issue #6. */
  private static void validate(String destination) {
    if (destination == null) {
      throw new InvalidDestinationException("Destination is required.");
    }
    URI uri;
    try {
      uri = new URI(destination);
    } catch (URISyntaxException e) {
      throw new InvalidDestinationException("Destination is not a valid URL.");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new InvalidDestinationException("Destination scheme must be http or https.");
    }
    if (uri.getHost() == null) {
      throw new InvalidDestinationException("Destination must include a host.");
    }
  }

  Optional<Link> resolve(String code) {
    return jdbcClient
        .sql("SELECT id, code, destination, created_at FROM links WHERE code = :code")
        .param("code", code)
        .query(Link.class)
        .optional();
  }
}
