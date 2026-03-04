package com.example.order.service;

import com.example.order.model.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderCompensationHandler {

    private final OrderClientAdapter orderClientAdapter;

    public OrderCompensationHandler(
            OrderClientAdapter orderClientAdapter
    ) {
        this.orderClientAdapter = orderClientAdapter;
    }

    public void compensateAfterCreateFailure(
            Order order,
            boolean paymentCaptured,
            boolean inventoryReserved,
            String correlationId
    ) {
        if (paymentCaptured) {
            try {
                orderClientAdapter.refundPayment(order, correlationId, true);
            } catch (Exception ignored) {
                // Retry-aware failure step is already recorded by the client adapter.
            }
        }

        if (inventoryReserved) {
            try {
                orderClientAdapter.releaseInventory(order, correlationId, true);
            } catch (Exception ignored) {
                // Retry-aware failure step is already recorded by the client adapter.
            }
        }
    }

    public void compensateBeforeCancel(Order order, String correlationId) {
        try {
            orderClientAdapter.refundPayment(order, correlationId, true);
        } catch (Exception ex) {
            throw new OrderWorkflowException(502, "PAYMENT_REFUND_FAILED", orderClientAdapter.extractFailureReason(ex));
        }

        try {
            orderClientAdapter.releaseInventory(order, correlationId, true);
        } catch (Exception ex) {
            throw new OrderWorkflowException(502, "INVENTORY_RELEASE_FAILED", orderClientAdapter.extractFailureReason(ex));
        }
    }

    public void releaseInventoryBeforeCancel(Order order, String correlationId) {
        try {
            orderClientAdapter.releaseInventory(order, correlationId, true);
        } catch (Exception ex) {
            throw new OrderWorkflowException(502, "INVENTORY_RELEASE_FAILED", orderClientAdapter.extractFailureReason(ex));
        }
    }
}
