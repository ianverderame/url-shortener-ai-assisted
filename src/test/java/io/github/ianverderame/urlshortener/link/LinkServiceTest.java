package io.github.ianverderame.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The link service's own rules, against in-memory fakes at the persistence and transaction
 * boundaries: policy first, a bounded retry on code conflicts only, one transaction per attempt.
 * The SQL itself is covered by the integration tests.
 */
class LinkServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
  private static final String EXHAUSTION_METRIC = "links.code.generation.exhausted";

  private final RecordingTransactions transactions = new RecordingTransactions();
  private final InMemoryLinkRepository links = new InMemoryLinkRepository(transactions);
  private final ScriptedGenerator generator = new ScriptedGenerator();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final LinkService service =
      new LinkService(
          links,
          transactions,
          generator,
          new DestinationPolicy(new PublicBaseUrl("https://sho.rt")),
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);

  @Test
  void createStoresTheNormalizedDestinationUnderAFreshCodeAtTheClocksTime() {
    generator.willReturn("Fresh001");

    Link link = service.create("https://MAIL.EXAMPLE/");

    assertThat(link.code()).isEqualTo("Fresh001");
    assertThat(link.destination()).isEqualTo("https://mail.example/");
    assertThat(link.createdAt()).isEqualTo(NOW);
    assertThat(links.findByCode("Fresh001")).contains(link);
    assertThat(transactions.count).isEqualTo(1);
  }

  @Test
  void aCodeConflictIsRetriedWithTheNextCode() {
    links.store("Taken001");
    generator.willReturn("Taken001", "Fresh002");

    assertThat(service.create("https://example.com/").code()).isEqualTo("Fresh002");
    assertThat(transactions.count).isEqualTo(2);
  }

  @Test
  void theThirdAttemptCanStillSucceed() {
    links.store("Taken001");
    links.store("Taken002");
    generator.willReturn("Taken001", "Taken002", "Fresh003");

    assertThat(service.create("https://example.com/").code()).isEqualTo("Fresh003");
    assertThat(exhaustions()).isZero();
  }

  @Test
  void afterThreeConflictsCreateGivesUpAndCountsTheExhaustionOnce() {
    links.store("Taken001");
    generator.willReturn("Taken001", "Taken001", "Taken001", "Unused04");

    assertThatExceptionOfType(CodeGenerationExhaustedException.class)
        .isThrownBy(() -> service.create("https://example.com/"));

    assertThat(generator.calls).isEqualTo(3);
    assertThat(transactions.count).isEqualTo(3);
    assertThat(exhaustions()).isEqualTo(1);
  }

  @Test
  void aStorageFailureIsNotRetried() {
    generator.willReturn("Fresh001", "Fresh002");
    links.failNextInsert();

    assertThatExceptionOfType(DataAccessResourceFailureException.class)
        .isThrownBy(() -> service.create("https://example.com/"));

    assertThat(generator.calls).isEqualTo(1);
    assertThat(exhaustions()).isZero();
  }

  @Test
  void aRejectedDestinationUsesNoCodeAndTouchesNoStorage() {
    assertThatExceptionOfType(InvalidDestinationException.class)
        .isThrownBy(() -> service.create("ftp://example.com/"));

    assertThat(generator.calls).isZero();
    assertThat(transactions.count).isZero();
  }

  @Test
  void resolveFindsAStoredLinkByItsExactCode() {
    Link stored = links.store("Abc12345");

    assertThat(service.resolve("Abc12345")).contains(stored);
    assertThat(service.resolve("abc12345")).isEmpty();
  }

  private double exhaustions() {
    return meters.get(EXHAUSTION_METRIC).counter().count();
  }

  /** Runs callbacks directly, counting them and tracking whether one is in progress. */
  static class RecordingTransactions implements TransactionOperations {

    int count;
    boolean active;

    @Override
    public <T> T execute(TransactionCallback<T> action) throws TransactionException {
      count++;
      active = true;
      try {
        return action.doInTransaction(new SimpleTransactionStatus());
      } finally {
        active = false;
      }
    }
  }

  /** Stores links in a map; inserting outside a transaction is a test failure. */
  static class InMemoryLinkRepository implements LinkRepository {

    private final RecordingTransactions transactions;
    private final Map<String, Link> byCode = new HashMap<>();
    private long nextId = 1;
    private boolean failNextInsert;

    InMemoryLinkRepository(RecordingTransactions transactions) {
      this.transactions = transactions;
    }

    Link store(String code) {
      Link link = new Link(nextId++, code, "https://example.com/", NOW);
      byCode.put(code, link);
      return link;
    }

    void failNextInsert() {
      failNextInsert = true;
    }

    @Override
    public Optional<Link> insertIfCodeFree(String code, String destination, Instant createdAt) {
      assertThat(transactions.active).as("insert runs inside a transaction").isTrue();
      if (failNextInsert) {
        failNextInsert = false;
        throw new DataAccessResourceFailureException("database unavailable");
      }
      if (byCode.containsKey(code)) {
        return Optional.empty();
      }
      Link link = new Link(nextId++, code, destination, createdAt);
      byCode.put(code, link);
      return Optional.of(link);
    }

    @Override
    public Optional<Link> findByCode(String code) {
      return Optional.ofNullable(byCode.get(code));
    }
  }

  /** Returns scripted codes in order and counts the calls. */
  static class ScriptedGenerator implements ShortCodeGenerator {

    private final Deque<String> codes = new ArrayDeque<>();
    int calls;

    void willReturn(String... next) {
      codes.addAll(List.of(next));
    }

    @Override
    public String generate() {
      calls++;
      String code = codes.poll();
      if (code == null) {
        throw new IllegalStateException("No scripted code left");
      }
      return code;
    }
  }
}
