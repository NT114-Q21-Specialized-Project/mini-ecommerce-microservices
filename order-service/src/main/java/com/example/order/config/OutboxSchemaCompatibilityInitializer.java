package com.example.order.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxSchemaCompatibilityInitializer {

    private static final Logger log = LoggerFactory.getLogger(OutboxSchemaCompatibilityInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public OutboxSchemaCompatibilityInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureOutboxSchemaCompatibility() {
        jdbcTemplate.execute("""
            ALTER TABLE outbox_events
            ADD COLUMN IF NOT EXISTS retry_count integer NOT NULL DEFAULT 0
        """);

        jdbcTemplate.execute("""
            ALTER TABLE outbox_events
            ADD COLUMN IF NOT EXISTS next_attempt_at timestamp with time zone
        """);

        jdbcTemplate.execute("""
            UPDATE outbox_events
            SET next_attempt_at = created_at
            WHERE next_attempt_at IS NULL
        """);

        jdbcTemplate.execute("""
            ALTER TABLE outbox_events
            ALTER COLUMN next_attempt_at SET NOT NULL
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_outbox_status_next_attempt_created
            ON outbox_events (status, next_attempt_at, created_at)
        """);

        log.info("Outbox schema compatibility check completed");
    }
}
