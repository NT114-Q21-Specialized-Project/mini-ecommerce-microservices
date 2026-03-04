package com.example.order.service;

import com.example.order.dto.OrderSagaStepView;
import com.example.order.model.Order;

import java.util.List;

public class OrderCreationResult {
    private final Order order;
    private final boolean idempotentReplay;
    private final String correlationId;
    private final List<OrderSagaStepView> sagaSteps;

    public OrderCreationResult(
            Order order,
            boolean idempotentReplay,
            String correlationId,
            List<OrderSagaStepView> sagaSteps
    ) {
        this.order = order;
        this.idempotentReplay = idempotentReplay;
        this.correlationId = correlationId;
        this.sagaSteps = sagaSteps;
    }

    public Order getOrder() {
        return order;
    }

    public boolean isIdempotentReplay() {
        return idempotentReplay;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public List<OrderSagaStepView> getSagaSteps() {
        return sagaSteps;
    }
}
