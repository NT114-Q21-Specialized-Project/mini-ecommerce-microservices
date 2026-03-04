package com.example.order.service;

import com.example.order.util.StructuredLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OrderEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final StructuredLogger structuredLogger;

    public OrderEventPublisher(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            StructuredLogger structuredLogger
    ) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.structuredLogger = structuredLogger;
    }

    public boolean publish(String channel, String payload, String correlationId) {
        if (redisTemplate == null) {
            return true;
        }

        try {
            redisTemplate.convertAndSend(channel, payload);
            structuredLogger.info("order.event.published", Map.of(
                    "channel", channel,
                    "correlation_id", correlationId
            ));
            return true;
        } catch (Exception e) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("channel", channel);
            fields.put("correlation_id", correlationId);
            fields.put("error", e.getMessage());
            structuredLogger.warn("order.event.publish_failed", fields);
            return false;
        }
    }
}
