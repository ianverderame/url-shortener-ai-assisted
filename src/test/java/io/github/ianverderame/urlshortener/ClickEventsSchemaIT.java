package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The click-events table, as migrated on real PostgreSQL, stores only a link reference and the
 * occurrence time: no identifiers, request data, or anything else about the visitor.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClickEventsSchemaIT {

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @Autowired JdbcClient jdbc;

  record Column(String name, String type, String nullable) {}

  @Test
  void holdsOnlyTheLinkReferenceAndTheOccurrenceTime() {
    List<Column> columns =
        jdbc.sql(
                """
                SELECT column_name AS name, data_type AS type, is_nullable AS nullable
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'click_events'
                ORDER BY ordinal_position
                """)
            .query(Column.class)
            .list();

    assertThat(columns)
        .containsExactly(
            new Column("link_id", "bigint", "NO"),
            new Column("occurred_at", "timestamp with time zone", "NO"));
  }

  @Test
  void linkIdReferencesLinks() {
    List<String> referenced =
        jdbc.sql(
                """
                SELECT ccu.table_name || '.' || ccu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_name = tc.constraint_name
                 AND kcu.table_schema = tc.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                 AND ccu.table_schema = tc.table_schema
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = 'click_events'
                  AND tc.constraint_type = 'FOREIGN KEY'
                  AND kcu.column_name = 'link_id'
                """)
            .query(String.class)
            .list();

    assertThat(referenced).containsExactly("links.id");
  }
}
