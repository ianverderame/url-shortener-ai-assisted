package io.github.ianverderame.urlshortener.link;

import java.time.Instant;

record LinkResponse(String code, String shortUrl, String destination, Instant createdAt) {}
