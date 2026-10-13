package io.github.ianverderame.urlshortener;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Clicks for a link over an inclusive range of UTC days. Missing bounds default to the 30 days
 * ending today; the range is checked before the code is looked up, so a bad range is a 400 whether
 * or not the code exists.
 */
@Service
class LinkStatsService {

  /** The default window, counting both ends: {@code to} and the 29 days before it. */
  private static final int DEFAULT_DAYS = 30;

  /** The longest allowed range, counting both ends. */
  private static final int MAX_DAYS = 366;

  private final LinkRepository links;
  private final ClickStatsRepository clickStats;
  private final Clock clock;

  LinkStatsService(LinkRepository links, ClickStatsRepository clickStats, Clock clock) {
    this.links = links;
    this.clickStats = clickStats;
    this.clock = clock;
  }

  /** {@code from} and {@code to} may be null, meaning the default. */
  LinkStats stats(String code, LocalDate from, LocalDate to) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate end = to != null ? to : today;
    LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
    validate(start, end, today);

    Link link = links.findByCode(code).orElseThrow(LinkNotFoundException::new);
    Map<LocalDate, Long> counts = clickStats.dailyClicks(link.id(), start, end);

    List<DailyClicks> days = new ArrayList<>();
    long total = 0;
    for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
      long clicks = counts.getOrDefault(day, 0L);
      days.add(new DailyClicks(day, clicks));
      total += clicks;
    }
    return new LinkStats(code, start, end, total, List.copyOf(days));
  }

  private static void validate(LocalDate start, LocalDate end, LocalDate today) {
    if (end.isAfter(today)) {
      throw new InvalidStatsRangeException("'to' must not be after today (UTC).");
    }
    if (start.isAfter(end)) {
      throw new InvalidStatsRangeException("'from' must not be after 'to'.");
    }
    if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
      throw new InvalidStatsRangeException(
          "The range may span at most " + MAX_DAYS + " days, counting both ends.");
    }
  }
}
