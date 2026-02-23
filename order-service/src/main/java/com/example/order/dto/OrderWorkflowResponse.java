package com.example.order.dto;

import com.example.order.model.Order;

import java.util.List;

public class OrderWorkflowResponse {

    private boolean idempotentReplay;
    private String correlationId;
    private Order order;
    private List<OrderSagaStepView> sagaSteps;

    public boolean isIdempotentReplay() {
        return idempotentReplay;
    }

    public void setIdempotentReplay(boolean idempotentReplay) {
        this.idempotentReplay = idempotentReplay;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public List<OrderSagaStepView> getSagaSteps() {
        return sagaSteps;
    }

    public void setSagaSteps(List<OrderSagaStepView> sagaSteps) {
        this.sagaSteps = sagaSteps;
    }
}
