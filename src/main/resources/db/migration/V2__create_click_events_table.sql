CREATE TABLE click_events (
    link_id BIGINT NOT NULL REFERENCES links (id),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX click_events_link_id_occurred_at_idx ON click_events (link_id, occurred_at);
