package com.example.order.service;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderSagaStepView;
import com.example.order.dto.PendingOutboxEventView;
import com.example.order.model.Order;
import com.example.order.model.OutboxStatus;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final SagaStepRecorder sagaStepRecorder;

    public OrderService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            OrderSagaOrchestrator orderSagaOrchestrator,
            SagaStepRecorder sagaStepRecorder
    ) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.sagaStepRecorder = sagaStepRecorder;
    }

    public OrderCreationResult createOrder(
            UUID authenticatedUserId,
            String authenticatedRole,
            String idempotencyKey,
            String correlationId,
            CreateOrderRequest request
    ) {
        return orderSagaOrchestrator.createOrder(
                authenticatedUserId,
                authenticatedRole,
                idempotencyKey,
                correlationId,
                request
        );
    }

    public List<Order> getOrders(UUID authenticatedUserId, String authenticatedRole, UUID requestedUserId) {
        validateAuthentication(authenticatedUserId, authenticatedRole);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(authenticatedRole);

        if (isAdmin) {
            if (requestedUserId != null) {
                return orderRepository.findByUserIdOrderByCreatedAtDesc(requestedUserId);
            }
            return orderRepository.findAllByOrderByCreatedAtDesc();
        }

        return orderRepository.findByUserIdOrderByCreatedAtDesc(authenticatedUserId);
    }

    public List<OrderSagaStepView> listOrderSagaSteps(UUID orderId, UUID authenticatedUserId, String authenticatedRole) {
        validateAuthentication(authenticatedUserId, authenticatedRole);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderWorkflowException(404, "ORDER_NOT_FOUND", "Order not found"));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(authenticatedRole);
        if (!isAdmin && !order.getUserId().equals(authenticatedUserId)) {
            throw new SecurityException("You can only view saga steps for your own orders");
        }

        return sagaStepRecorder.toViews(orderId);
    }

    public Order cancelOrder(UUID orderId, UUID authenticatedUserId, String authenticatedRole, String correlationId) {
        return orderSagaOrchestrator.cancelOrder(orderId, authenticatedUserId, authenticatedRole, correlationId);
    }

    public List<PendingOutboxEventView> getPendingOutboxEvents(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<PendingOutboxEventView> events = outboxEventRepository.findPendingViewByStatuses(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED)
        );
        if (events.size() <= safeLimit) {
            return events;
        }
        return events.subList(0, safeLimit);
    }

    private void validateAuthentication(UUID authenticatedUserId, String authenticatedRole) {
        if (authenticatedUserId == null) {
            throw new SecurityException("Missing authenticated user");
        }

        if (authenticatedRole == null || authenticatedRole.isBlank()) {
            throw new SecurityException("Missing authenticated role");
        }
    }
}
