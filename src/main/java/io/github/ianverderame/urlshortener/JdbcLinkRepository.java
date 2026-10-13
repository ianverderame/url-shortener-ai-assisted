package io.github.ianverderame.urlshortener;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLinkRepository implements LinkRepository {

  private final JdbcClient jdbcClient;

  JdbcLinkRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /** A taken code is an empty {@code RETURNING}, not an exception, so no transaction is spoiled. */
  @Override
  public Optional<Link> insertIfCodeFree(String code, String destination, Instant createdAt) {
    return jdbcClient
        .sql(
            "INSERT INTO links (code, destination, created_at) "
                + "VALUES (:code, :destination, :createdAt) "
                + "ON CONFLICT (code) DO NOTHING "
                + "RETURNING id, code, destination, created_at")
        .param("code", code)
        .param("destination", destination)
        .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
        .query(Link.class)
        .optional();
  }

  @Override
  public Optional<Link> findByCode(String code) {
    return jdbcClient
        .sql("SELECT id, code, destination, created_at FROM links WHERE code = :code")
        .param("code", code)
        .query(Link.class)
        .optional();
  }
}
