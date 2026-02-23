package com.example.payment.service;

import com.example.payment.util.StructuredLogger;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PaymentEventSubscriber implements MessageListener {

    private final StructuredLogger structuredLogger;

    public PaymentEventSubscriber(StructuredLogger structuredLogger) {
        this.structuredLogger = structuredLogger;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        structuredLogger.info("payment.event.consumed", Map.of(
                "channel", "orders.events",
                "payload", payload
        ));
    }
}
