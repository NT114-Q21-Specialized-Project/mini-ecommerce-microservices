package com.example.order.service;

import com.example.order.model.OutboxEvent;
import com.example.order.model.OutboxStatus;
import com.example.order.repository.OutboxEventRepository;
import com.example.order.util.StructuredLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(value = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherWorker {

    private static final List<OutboxStatus> RETRIABLE_STATUSES = List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final StructuredLogger structuredLogger;
    private final String outboxChannel;
    private final int outboxBatchSize;
    private final int maxRetryAttempts;
    private final long initialBackoffMs;

    public OutboxPublisherWorker(
            OutboxEventRepository outboxEventRepository,
            OrderEventPublisher orderEventPublisher,
            StructuredLogger structuredLogger,
            @Value("${outbox.publisher.channel:orders.events}") String outboxChannel,
            @Value("${outbox.publisher.batch-size:50}") int outboxBatchSize,
            @Value("${outbox.publisher.max-retry-attempts:8}") int maxRetryAttempts,
            @Value("${outbox.publisher.initial-backoff-ms:500}") long initialBackoffMs
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.structuredLogger = structuredLogger;
        this.outboxChannel = outboxChannel;
        this.outboxBatchSize = Math.max(1, outboxBatchSize);
        this.maxRetryAttempts = Math.max(1, maxRetryAttempts);
        this.initialBackoffMs = Math.max(100, initialBackoffMs);
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:1000}")
    public void runScheduled() {
        publishAvailableEvents();
    }

    @Transactional
    public int publishAvailableEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> readyEvents = outboxEventRepository.findReadyForPublish(
                RETRIABLE_STATUSES,
                now,
                maxRetryAttempts,
                PageRequest.of(0, outboxBatchSize)
        );

        int processedCount = 0;
        for (OutboxEvent event : readyEvents) {
            processedCount++;
            boolean published = orderEventPublisher.publish(outboxChannel, event.getPayload(), event.getCorrelationId());
            if (published) {
                markPublished(event);
            } else {
                markFailed(event, "Publisher returned unsuccessful result");
            }
        }
        return processedCount;
    }

    private void markPublished(OutboxEvent event) {
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        event.setLastError(null);
        outboxEventRepository.save(event);
    }

    private void markFailed(OutboxEvent event, String error) {
        int currentRetryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
        int nextRetryCount = currentRetryCount + 1;
        event.setStatus(OutboxStatus.FAILED);
        event.setRetryCount(nextRetryCount);
        event.setLastError(error);
        event.setNextAttemptAt(Instant.now().plusMillis(computeBackoffMs(nextRetryCount)));
        outboxEventRepository.save(event);

        structuredLogger.warn("order.outbox.publish_failed", Map.of(
                "event_id", event.getId().toString(),
                "aggregate_id", event.getAggregateId().toString(),
                "retry_count", nextRetryCount
        ));
    }

    private long computeBackoffMs(int attemptNumber) {
        long backoffMs = initialBackoffMs * (1L << Math.max(0, attemptNumber - 1));
        return Math.min(backoffMs, 30000);
    }
}
