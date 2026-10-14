package io.github.ianverderame.urlshortener.analytics;

import java.time.LocalDate;
import java.util.List;

/** A link's clicks over an inclusive range of UTC days: the total and every day, oldest first. */
record LinkStats(String code, LocalDate from, LocalDate to, long total, List<DailyClicks> days) {}
