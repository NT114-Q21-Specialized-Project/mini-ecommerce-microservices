package com.example.order.service;

import com.example.order.model.Order;
import com.example.order.model.OrderEventType;
import com.example.order.model.OutboxEvent;
import com.example.order.model.OutboxStatus;
import com.example.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderOutboxService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueueOrderEvent(OrderEventType eventType, Order order, UUID actorUserId, String correlationId) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("ORDER");
        outboxEvent.setAggregateId(order.getId());
        outboxEvent.setEventType(eventType.name());
        outboxEvent.setPayload(buildOutboxPayload(eventType, order, actorUserId, correlationId));
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setRetryCount(0);
        outboxEvent.setLastError(null);
        outboxEvent.setPublishedAt(null);
        outboxEvent.setCorrelationId(correlationId);
        outboxEvent.setNextAttemptAt(Instant.now());
        outboxEventRepository.save(outboxEvent);
    }

    private String buildOutboxPayload(OrderEventType eventType, Order order, UUID actorUserId, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType.name());
        payload.put("occurredAt", Instant.now());
        payload.put("actorUserId", actorUserId);
        payload.put("orderId", order.getId());
        payload.put("userId", order.getUserId());
        payload.put("productId", order.getProductId());
        payload.put("quantity", order.getQuantity());
        payload.put("unitPrice", order.getUnitPrice());
        payload.put("totalAmount", order.getTotalAmount());
        payload.put("status", order.getStatus());
        payload.put("failureReason", order.getFailureReason());
        payload.put("correlationId", correlationId);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox payload", e);
        }
    }
}
