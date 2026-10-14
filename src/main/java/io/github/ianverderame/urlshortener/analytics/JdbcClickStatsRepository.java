package io.github.ianverderame.urlshortener.analytics;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcClickStatsRepository implements ClickStatsRepository {

  private final JdbcClient jdbcClient;

  JdbcClickStatsRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * The inclusive days become the half-open instant range [from 00:00Z, to+1 00:00Z), which the
   * (link_id, occurred_at) index serves; each click is bucketed by its UTC date.
   */
  @Override
  public Map<LocalDate, Long> dailyClicks(long linkId, LocalDate from, LocalDate to) {
    Map<LocalDate, Long> counts = new HashMap<>();
    jdbcClient
        .sql(
            "SELECT (occurred_at AT TIME ZONE 'UTC')::date AS day, count(*) AS clicks "
                + "FROM click_events "
                + "WHERE link_id = :linkId AND occurred_at >= :start AND occurred_at < :end "
                + "GROUP BY day")
        .param("linkId", linkId)
        .param("start", startOfDay(from))
        .param("end", startOfDay(to.plusDays(1)))
        .query(
            (rs, rowNum) -> counts.put(rs.getObject("day", LocalDate.class), rs.getLong("clicks")))
        .list();
    return counts;
  }

  private static OffsetDateTime startOfDay(LocalDate day) {
    return day.atStartOfDay().atOffset(ZoneOffset.UTC);
  }
}
