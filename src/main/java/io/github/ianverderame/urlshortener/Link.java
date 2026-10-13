package io.github.ianverderame.urlshortener;

import java.time.Instant;

record Link(long id, String code, String destination, Instant createdAt) {}
