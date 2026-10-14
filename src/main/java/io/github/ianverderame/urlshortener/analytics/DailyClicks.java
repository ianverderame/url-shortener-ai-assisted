package io.github.ianverderame.urlshortener.analytics;

import java.time.LocalDate;

record DailyClicks(LocalDate date, long clicks) {}
