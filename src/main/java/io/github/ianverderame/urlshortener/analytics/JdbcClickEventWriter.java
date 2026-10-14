package io.github.ianverderame.urlshortener.analytics;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcClickEventWriter implements ClickEventWriter {

  private final JdbcClient jdbcClient;

  JdbcClickEventWriter(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void write(long linkId, Instant occurredAt) {
    jdbcClient
        .sql("INSERT INTO click_events (link_id, occurred_at) VALUES (:linkId, :occurredAt)")
        .param("linkId", linkId)
        .param("occurredAt", OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
        .update();
  }
}
