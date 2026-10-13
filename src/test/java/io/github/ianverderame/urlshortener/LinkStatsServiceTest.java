package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The stats rules, against in-memory fakes at the persistence boundary and a fixed clock: the
 * default window, inclusive bounds, zero-filled days, and the range limits, checked before the code
 * is looked up. The SQL, including UTC day boundaries, is covered by the integration tests.
 */
class LinkStatsServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.parse("2026-10-13");
  private static final String CODE = "Stats001";

  private final FakeLinks links = new FakeLinks();
  private final FakeClickStats clickStats = new FakeClickStats();
  private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void theDefaultWindowIsTheThirtyDaysEndingToday() {
    links.store(CODE);

    LinkStats stats = service().stats(CODE, null, null);

    assertThat(stats.from()).isEqualTo(LocalDate.parse("2026-09-14"));
    assertThat(stats.to()).isEqualTo(TODAY);
    assertThat(stats.days()).hasSize(30);
  }

  @Test
  void todayIsTheUtcDateWhateverTheClocksZone() {
    links.store(CODE);
    clock = Clock.fixed(Instant.parse("2026-10-13T23:30:00Z"), ZoneOffset.ofHours(14));

    assertThat(service().stats(CODE, null, null).to()).isEqualTo(TODAY);
  }

  @Test
  void aGivenToMovesTheDefaultWindowWithIt() {
    links.store(CODE);

    LinkStats stats = service().stats(CODE, null, LocalDate.parse("2026-06-30"));

    assertThat(stats.from()).isEqualTo(LocalDate.parse("2026-06-01"));
    assertThat(stats.to()).isEqualTo(LocalDate.parse("2026-06-30"));
  }

  @Test
  void aGivenFromRunsToToday() {
    links.store(CODE);

    LinkStats stats = service().stats(CODE, LocalDate.parse("2026-10-01"), null);

    assertThat(stats.from()).isEqualTo(LocalDate.parse("2026-10-01"));
    assertThat(stats.to()).isEqualTo(TODAY);
  }

  @Test
  void everyDayInTheRangeIsListedInOrderWithZeroForDaysWithoutClicks() {
    Link link = links.store(CODE);
    clickStats.put(link.id(), "2026-10-02", 3);
    clickStats.put(link.id(), "2026-10-04", 1);

    LinkStats stats =
        service().stats(CODE, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-05"));

    assertThat(stats.code()).isEqualTo(CODE);
    assertThat(stats.days())
        .containsExactly(
            new DailyClicks(LocalDate.parse("2026-10-01"), 0),
            new DailyClicks(LocalDate.parse("2026-10-02"), 3),
            new DailyClicks(LocalDate.parse("2026-10-03"), 0),
            new DailyClicks(LocalDate.parse("2026-10-04"), 1),
            new DailyClicks(LocalDate.parse("2026-10-05"), 0));
    assertThat(stats.total()).isEqualTo(4);
  }

  @Test
  void bothBoundsAreInclusive() {
    Link link = links.store(CODE);
    clickStats.put(link.id(), "2026-10-01", 2);
    clickStats.put(link.id(), "2026-10-03", 5);

    LinkStats stats =
        service().stats(CODE, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"));

    assertThat(stats.total()).isEqualTo(7);
    assertThat(clickStats.requested)
        .isEqualTo(new Range(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03")));
  }

  @Test
  void aSingleDayIsAValidRange() {
    links.store(CODE);

    LinkStats stats = service().stats(CODE, TODAY, TODAY);

    assertThat(stats.days()).containsExactly(new DailyClicks(TODAY, 0));
  }

  @Test
  void aSpanOf366DaysIsAllowed() {
    links.store(CODE);

    LinkStats stats = service().stats(CODE, TODAY.minusDays(365), TODAY);

    assertThat(stats.days()).hasSize(366);
  }

  @Test
  void aSpanOf367DaysIsRejected() {
    links.store(CODE);

    assertThatExceptionOfType(InvalidStatsRangeException.class)
        .isThrownBy(() -> service().stats(CODE, TODAY.minusDays(366), TODAY));
  }

  @Test
  void fromAfterToIsRejected() {
    links.store(CODE);

    assertThatExceptionOfType(InvalidStatsRangeException.class)
        .isThrownBy(() -> service().stats(CODE, TODAY, TODAY.minusDays(1)));
  }

  @Test
  void toAfterTodayIsRejected() {
    links.store(CODE);

    assertThatExceptionOfType(InvalidStatsRangeException.class)
        .isThrownBy(() -> service().stats(CODE, TODAY, TODAY.plusDays(1)));
  }

  @Test
  void anUnknownCodeIsNotFound() {
    assertThatExceptionOfType(LinkNotFoundException.class)
        .isThrownBy(() -> service().stats("Unknown1", null, null));
  }

  @Test
  void theRangeIsValidatedBeforeTheCodeIsLookedUp() {
    assertThatExceptionOfType(InvalidStatsRangeException.class)
        .isThrownBy(() -> service().stats("Unknown1", TODAY, TODAY.plusDays(1)));
    assertThat(links.lookups).isZero();
  }

  private LinkStatsService service() {
    return new LinkStatsService(links, clickStats, clock);
  }

  record Range(LocalDate from, LocalDate to) {}

  static final class FakeLinks implements LinkRepository {

    private final Map<String, Link> byCode = new HashMap<>();
    private long nextId = 1;
    int lookups;

    Link store(String code) {
      Link link = new Link(nextId++, code, "https://example.com/", NOW);
      byCode.put(code, link);
      return link;
    }

    @Override
    public Optional<Link> insertIfCodeFree(String code, String destination, Instant createdAt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Link> findByCode(String code) {
      lookups++;
      return Optional.ofNullable(byCode.get(code));
    }
  }

  static final class FakeClickStats implements ClickStatsRepository {

    private final Map<Long, Map<LocalDate, Long>> byLink = new HashMap<>();
    Range requested;

    void put(long linkId, String date, long clicks) {
      byLink.computeIfAbsent(linkId, id -> new HashMap<>()).put(LocalDate.parse(date), clicks);
    }

    @Override
    public Map<LocalDate, Long> dailyClicks(long linkId, LocalDate from, LocalDate to) {
      requested = new Range(from, to);
      Map<LocalDate, Long> inRange = new HashMap<>();
      byLink
          .getOrDefault(linkId, Map.of())
          .forEach(
              (date, clicks) -> {
                if (!date.isBefore(from) && !date.isAfter(to)) {
                  inRange.put(date, clicks);
                }
              });
      return inRange;
    }
  }
}
