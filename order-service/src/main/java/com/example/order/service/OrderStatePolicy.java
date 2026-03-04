package com.example.order.service;

import com.example.order.model.Order;
import com.example.order.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStatePolicy {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = buildAllowedTransitions();

    public void initialize(Order order) {
        order.setStatus(OrderStatus.CREATED);
        order.setFailureReason(null);
        order.setCancelledAt(null);
    }

    public void transition(Order order, OrderStatus targetStatus, String failureReason) {
        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == targetStatus) {
            if (targetStatus == OrderStatus.FAILED) {
                order.setFailureReason(failureReason);
            }
            return;
        }

        if (currentStatus != null) {
            Set<OrderStatus> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(OrderStatus.class));
            if (!allowedTargets.contains(targetStatus)) {
                throw new OrderWorkflowException(
                        409,
                        "INVALID_ORDER_STATE_TRANSITION",
                        "Cannot transition order from " + currentStatus + " to " + targetStatus
                );
            }
        }

        order.setStatus(targetStatus);
        if (targetStatus == OrderStatus.FAILED) {
            order.setFailureReason(failureReason);
        } else {
            order.setFailureReason(null);
        }

        if (targetStatus == OrderStatus.CANCELLED && order.getCancelledAt() == null) {
            order.setCancelledAt(Instant.now());
        }
    }

    public boolean canCancel(OrderStatus status) {
        return status != null && status != OrderStatus.FAILED && status != OrderStatus.CANCELLED;
    }

    private static Map<OrderStatus, Set<OrderStatus>> buildAllowedTransitions() {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(OrderStatus.CREATED, EnumSet.of(
                OrderStatus.INVENTORY_RESERVED,
                OrderStatus.FAILED,
                OrderStatus.CANCELLED
        ));
        transitions.put(OrderStatus.INVENTORY_RESERVED, EnumSet.of(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.FAILED,
                OrderStatus.CANCELLED
        ));
        transitions.put(OrderStatus.PAYMENT_PENDING, EnumSet.of(
                OrderStatus.CONFIRMED,
                OrderStatus.FAILED,
                OrderStatus.CANCELLED
        ));
        transitions.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.CANCELLED));
        transitions.put(OrderStatus.FAILED, EnumSet.noneOf(OrderStatus.class));
        transitions.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        return transitions;
    }
}
