package io.github.ianverderame.urlshortener;

import java.time.LocalDate;

record DailyClicks(LocalDate date, long clicks) {}
