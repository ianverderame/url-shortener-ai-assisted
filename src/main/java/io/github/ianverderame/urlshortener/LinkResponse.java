package io.github.ianverderame.urlshortener;

import java.time.Instant;

record LinkResponse(String code, String shortUrl, String destination, Instant createdAt) {}
