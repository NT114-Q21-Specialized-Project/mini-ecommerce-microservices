package com.example.order.dto;

import java.time.Instant;
import java.util.UUID;

public class PendingOutboxEventView {
    private final UUID id;
    private final String aggregateType;
    private final UUID aggregateId;
    private final String eventType;
    private final String status;
    private final Instant createdAt;
    private final Instant publishedAt;

    public PendingOutboxEventView(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String status,
            Instant createdAt,
            Instant publishedAt
    ) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.status = status;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
