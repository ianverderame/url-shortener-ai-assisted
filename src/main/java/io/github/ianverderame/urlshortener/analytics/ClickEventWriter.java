package io.github.ianverderame.urlshortener.analytics;

import java.time.Instant;

/** Where click events are stored: only the link and the time it was followed. */
interface ClickEventWriter {

  void write(long linkId, Instant occurredAt);
}
