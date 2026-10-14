package io.github.ianverderame.urlshortener.link;

import java.time.Instant;

public record Link(long id, String code, String destination, Instant createdAt) {}
