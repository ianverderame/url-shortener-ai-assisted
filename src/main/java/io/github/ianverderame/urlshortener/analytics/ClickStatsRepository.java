package io.github.ianverderame.urlshortener.analytics;

import java.time.LocalDate;
import java.util.Map;

/** Reads stored clicks back as counts per UTC day. */
interface ClickStatsRepository {

  /**
   * Clicks per UTC day from {@code from} to {@code to}, both inclusive; days without clicks are
   * absent.
   */
  Map<LocalDate, Long> dailyClicks(long linkId, LocalDate from, LocalDate to);
}
