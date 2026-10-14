package io.github.ianverderame.urlshortener.link;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
class LinkService {

  private static final Logger log = LoggerFactory.getLogger(LinkService.class);

  /** Attempts per create; with 62^8 codes, more than one conflict in a row is an anomaly. */
  private static final int MAX_CODE_ATTEMPTS = 3;

  private static final String EXHAUSTION_METRIC = "links.code.generation.exhausted";

  private final LinkRepository links;
  private final TransactionOperations transactions;
  private final ShortCodeGenerator shortCodeGenerator;
  private final DestinationPolicy destinationPolicy;
  private final Clock clock;
  private final Counter exhaustionCounter;

  LinkService(
      LinkRepository links,
      TransactionOperations transactions,
      ShortCodeGenerator shortCodeGenerator,
      DestinationPolicy destinationPolicy,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.links = links;
    this.transactions = transactions;
    this.shortCodeGenerator = shortCodeGenerator;
    this.destinationPolicy = destinationPolicy;
    this.clock = clock;
    this.exhaustionCounter =
        Counter.builder(EXHAUSTION_METRIC)
            .description("Link creations that gave up after repeated short-code conflicts")
            .register(meterRegistry);
  }

  /**
   * Inserts the link under a fresh code, storing the policy's normalized destination. Only a code
   * conflict (the code is already taken) leads to another attempt.
   *
   * <p>Each attempt runs in its own transaction even though it is a single statement, so that a
   * {@code 503} stores nothing. If the database hangs after the INSERT is sent, the socket timeout
   * gives up on it; when the database resumes, an autocommitted INSERT would still run and commit,
   * storing a link the client was told failed. Inside a transaction the INSERT waits for a COMMIT
   * that never comes, and the server rolls it back once it sees the connection is gone. (#39
   * considered removing the transaction and kept it for this reason.)
   */
  Link create(String submitted) {
    String destination = destinationPolicy.normalize(submitted);
    for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
      String code = shortCodeGenerator.generate();
      Instant createdAt = Instant.now(clock);
      Optional<Link> inserted =
          transactions.execute(status -> links.insertIfCodeFree(code, destination, createdAt));
      if (inserted.isPresent()) {
        // Code and host only: the full destination may carry tokens in its path or query.
        log.info("Created link code={} host={}", code, URI.create(destination).getHost());
        return inserted.get();
      }
    }
    exhaustionCounter.increment();
    throw new CodeGenerationExhaustedException();
  }
}
